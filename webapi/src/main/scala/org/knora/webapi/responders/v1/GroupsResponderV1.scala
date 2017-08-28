/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
 * This file is part of Knora.
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.responders.v1

import java.util.UUID

import akka.actor.Status
import akka.pattern._
import org.knora.webapi
import org.knora.webapi.messages.v1.responder.groupmessages._
import org.knora.webapi.messages.v1.responder.projectmessages.ProjectsResponderRequestV1
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.messages.v1.store.triplestoremessages._
import org.knora.webapi.responders.IriLocker
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util.KnoraIdUtil
import org.knora.webapi.{DuplicateValueException, _}

import scala.concurrent.Future


/**
  * Returns information about Knora projects.
  */
class GroupsResponderV1 extends ResponderV1 {

    // Creates IRIs for new Knora user objects.
    val knoraIdUtil = new KnoraIdUtil

    // Global lock IRI used for group creation and updating
    val GROUPS_GLOBAL_LOCK_IRI = "http://data.knora.org/users"

    val PROJECT_MEMBER = "ProjectMember"
    val PROJECT_ADMIN = "ProjectAdmin"

    /**
      * Receives a message extending [[ProjectsResponderRequestV1]], and returns an appropriate response message, or
      * [[Status.Failure]]. If a serious error occurs (i.e. an error that isn't the client's fault), this
      * method first returns `Failure` to the sender, then throws an exception.
      */
    def receive = {
        case GroupsGetRequestV1(userProfile) => future2Message(sender(), getGroupsResponseV1(userProfile), log)
        case GroupInfoByIRIGetRequest(iri, userProfile) => future2Message(sender(), getGroupInfoByIRIGetRequest(iri, userProfile), log)
        case GroupInfoByNameGetRequest(projectIri, groupName, userProfile) => future2Message(sender(), getGroupInfoByNameGetRequest(projectIri, groupName, userProfile), log)
        case GroupCreateRequestV1(newGroupInfo, userProfile, apiRequestID) => future2Message(sender(), createGroupV1(newGroupInfo, userProfile, apiRequestID), log)
        case other => handleUnexpectedMessage(sender(), other, log, this.getClass.getName)
    }

    /**
      * Gets all the groups and returns them as a [[GroupsResponseV1]].
      *
      * @param userProfile the profile of the user that is making the request.
      * @return all the groups as a [[GroupsResponseV1]].
      */
    private def getGroupsResponseV1(userProfile: Option[UserProfileV1]): Future[GroupsResponseV1] = {

        for {
            sparqlQuery <- Future(queries.sparql.v1.txt.getGroups(
                triplestore = settings.triplestoreType
            ).toString())
            groupsResponse <- (storeManager ? SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResponse]
            groupsResponseRows: Seq[VariableResultsRow] = groupsResponse.results.bindings

            groupsWithProperties: Map[String, Map[String, String]] = groupsResponseRows.groupBy(_.rowMap("s")).map {
                case (groupIri: String, rows: Seq[VariableResultsRow]) => (groupIri, rows.map {
                    case row => (row.rowMap("p"), row.rowMap("o"))
                }.toMap)
            }

            groups = groupsWithProperties.map {
                case (groupIri: IRI, propsMap: Map[String, String]) =>

                    GroupInfoV1(
                        id = groupIri,
                        name = propsMap.getOrElse(OntologyConstants.KnoraBase.GroupName, throw InconsistentTriplestoreDataException(s"Group $groupIri has no name attached")),
                        description = propsMap.get(OntologyConstants.KnoraBase.GroupDescription),
                        belongsToProject = propsMap.getOrElse(OntologyConstants.KnoraBase.BelongsToProject, throw InconsistentTriplestoreDataException(s"Group $groupIri has no project attached")),
                        status = propsMap(OntologyConstants.KnoraBase.Status).toBoolean,
                        hasSelfJoinEnabled = propsMap(OntologyConstants.KnoraBase.HasSelfJoinEnabled).toBoolean
                    )
            }.toVector
        } yield GroupsResponseV1(
            groups = groups
        )
    }

    /**
      * Gets the group with the given group Iri and returns the information as a [[GroupInfoResponseV1]].
      *
      * @param groupIRI the Iri of the group requested.
      * @param userProfile the profile of user that is making the request.
      * @return information about the group as a [[GroupInfoResponseV1]].
      */
    private def getGroupInfoByIRIGetRequest(groupIRI: IRI, userProfile: Option[UserProfileV1] = None): Future[GroupInfoResponseV1] = {
        for {
            sparqlQuery <- Future(queries.sparql.v1.txt.getGroupByIri(
                triplestore = settings.triplestoreType,
                groupIri = groupIRI
            ).toString())
            groupResponse <- (storeManager ? SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResponse]

            // check group response
            _ = if (groupResponse.results.bindings.isEmpty) {
                throw NotFoundException(s"For the given group iri '$groupIRI' no information was found")
            }

            // get group info
            groupInfo = createGroupInfoV1FromGroupResponse(groupResponse = groupResponse.results.bindings, groupIri = groupIRI, userProfile)

        } yield GroupInfoResponseV1(
            group_info = groupInfo
        )
    }

    /**
      * Gets the group with the given name and returns the information as a [[GroupInfoResponseV1]].
      *
      * @param projectIRI the IRI of the project, the group is part of.
      * @param groupName the name of the group requested.
      * @param userProfile the profile of user that is making the request.
      * @return information about the group as a [[GroupInfoResponseV1]].
      */
    private def getGroupInfoByNameGetRequest(projectIRI: IRI, groupName: String, userProfile: Option[UserProfileV1]): Future[GroupInfoResponseV1] = {

        /* Check to see if it is a built-in implicit group and skip sparql query */

        groupName match {
            case PROJECT_MEMBER => getGroupInfoForProjectMemberGroup(projectIRI, userProfile)
            case PROJECT_ADMIN => getGroupInfoForProjectAdminGroup(projectIRI, userProfile)
            case _ => getGroupInfoByNameFromTriplestore(projectIRI, groupName, userProfile)
        }
    }

    private def getGroupInfoForProjectMemberGroup(projectIRI: IRI, userProfile: Option[UserProfileV1]): Future[GroupInfoResponseV1] = {

        val groupInfo = GroupInfoV1(
            id = "-",
            name = "ProjectMember",
            description = Some("Default Project Member Group"),
            belongsToProject = projectIRI,
            status = true,
            hasSelfJoinEnabled = false)

        val groupInfoResponse = GroupInfoResponseV1(
            group_info = groupInfo
        )

        Future(groupInfoResponse)
    }

    private def getGroupInfoForProjectAdminGroup(projectIRI: IRI, userProfile: Option[UserProfileV1]): Future[GroupInfoResponseV1] = {

        val groupInfo = GroupInfoV1(
            id = "-",
            name = "ProjectAdmin",
            description = Some("Default Project Admin Group"),
            belongsToProject = projectIRI,
            status = true,
            hasSelfJoinEnabled = false)

        val groupInfoResponse = GroupInfoResponseV1(
            group_info = groupInfo
        )

        Future(groupInfoResponse)
    }

    private def getGroupInfoByNameFromTriplestore(projectIRI: IRI, groupName: String, userProfile: Option[UserProfileV1]): Future[GroupInfoResponseV1] = {
        for {
            sparqlQuery <- Future(queries.sparql.v1.txt.getGroupByName(
                triplestore = settings.triplestoreType,
                name = groupName,
                projectIri = projectIRI
            ).toString())
            _ = log.debug(s"getGroupInfoByNameGetRequest - getGroupByName: $sparqlQuery")
            groupResponse <- (storeManager ? SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResponse]

            _ = log.debug(s"group response: ${groupResponse.toString}")

            // check group response and get group Iri
            groupIri: IRI = if (groupResponse.results.bindings.nonEmpty) {
                groupResponse.results.bindings.head.rowMap("s")
            } else {
                throw NotFoundException(s"For the given group name '$groupName' no information was found")
            }

            // get group info
            groupInfo = createGroupInfoV1FromGroupResponse(
                groupResponse = groupResponse.results.bindings,
                groupIri = groupIri,
                userProfile
            )

            groupInfoResponse = GroupInfoResponseV1(
                group_info = groupInfo
            )
        } yield groupInfoResponse
    }

    private def createGroupV1(createRequest: CreateGroupApiRequestV1, userProfile: UserProfileV1, apiRequestID: UUID): Future[GroupOperationResponseV1] = {

        def createGroupTask(createRequest: CreateGroupApiRequestV1, userProfile: UserProfileV1, apiRequestID: UUID): Future[GroupOperationResponseV1] = for {
            /* check if username or password are not empty */
            _ <- Future(if (createRequest.name.isEmpty) throw BadRequestException("Group name cannot be empty"))
            _ = if (createRequest.belongsToProject.isEmpty) throw BadRequestException("Project IRI cannot be empty")

            /* check if the supplied group name is unique inside the project, i.e. not already registered */
            sparqlQueryString = queries.sparql.v1.txt.getGroupByName(
                triplestore = settings.triplestoreType,
                name = createRequest.name,
                projectIri = createRequest.belongsToProject
            ).toString()
            _ = log.debug(s"createGroupV1 - check duplicate name: $sparqlQueryString")
            groupQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]

            //_ = log.debug(MessageUtil.toSource(userDataQueryResponse))

            _ = if (groupQueryResponse.results.bindings.nonEmpty) {
                throw DuplicateValueException(s"Group with the name: '${createRequest.name}' already exists")
            }

            /* generate a new random group IRI */
            groupIRI = knoraIdUtil.makeRandomGroupIri

            /* create the group */
            createNewGroupSparqlString = queries.sparql.v1.txt.createNewGroup(
                adminNamedGraphIri = "http://www.knora.org/data/admin",
                triplestore = settings.triplestoreType,
                groupIri = groupIRI,
                groupClassIri = OntologyConstants.KnoraBase.UserGroup,
                name = createRequest.name,
                maybeDescription = createRequest.description,
                projectIri = createRequest.belongsToProject,
                status = createRequest.status,
                hasSelfJoinEnabled = createRequest.hasSelfJoinEnabled
            ).toString
            _ = log.debug(s"createGroupV1 - createNewGroup: $createNewGroupSparqlString")
            createGroupResponse <- (storeManager ? SparqlUpdateRequest(createNewGroupSparqlString)).mapTo[SparqlUpdateResponse]

            /* Verify that the group was created */
            sparqlQuery <- Future(queries.sparql.v1.txt.getGroupByIri(
                triplestore = settings.triplestoreType,
                groupIri = groupIRI
            ).toString())
            groupResponse <- (storeManager ? SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResponse]

            // check group response
            _ = if (groupResponse.results.bindings.isEmpty) {
                throw NotFoundException(s"User $groupIRI was not created. Please report this as a possible bug.")
            }

            // create group info from group response
            groupInfo = createGroupInfoV1FromGroupResponse(
                groupResponse = groupResponse.results.bindings,
                groupIri = groupIRI,
                Some(userProfile)
            )

            /* create the group operation response */
            groupOperationResponseV1 = GroupOperationResponseV1(groupInfo)

        } yield groupOperationResponseV1

        for {
            // run user creation with an global IRI lock
            taskResult <- IriLocker.runWithIriLock(
                apiRequestID,
                GROUPS_GLOBAL_LOCK_IRI,
                () => createGroupTask(createRequest, userProfile, apiRequestID)
            )
        } yield taskResult
    }


    private def updateGroupInfoV1(groupIri: webapi.IRI,
                                  propertyIri: webapi.IRI,
                                  newValue: Any,
                                  userProfile: UserProfileV1,
                                  apiRequestID: UUID) = ???

    private def updateGroupPermissionV1(userProfile: UserProfileV1,
                                        apiRequestID: UUID) = ???

    ////////////////////
    // Helper Methods //
    ////////////////////

    /**
      * Helper method that turns SPARQL result rows into a [[GroupInfoV1]].
      *
      * @param groupResponse results from the SPARQL query representing information about the group.
      * @param groupIri the Iri of the group the querid information belong to.
      * @param userProfile the profile of the user that is making the request.
      * @return a [[GroupInfoV1]] representing information about the group.
      */
    private def createGroupInfoV1FromGroupResponse(groupResponse: Seq[VariableResultsRow], groupIri: IRI, userProfile: Option[UserProfileV1]): GroupInfoV1 = {

        val groupProperties = groupResponse.foldLeft(Map.empty[IRI, String]) {
            case (acc, row: VariableResultsRow) =>
                acc + (row.rowMap("p") -> row.rowMap("o"))
        }

        log.debug(s"group properties: ${groupProperties.toString}")

        GroupInfoV1(
            id = groupIri,
            name = groupProperties.getOrElse(OntologyConstants.KnoraBase.GroupName, throw InconsistentTriplestoreDataException(s"Group $groupIri has no groupName attached")),
            description = groupProperties.get(OntologyConstants.KnoraBase.GroupDescription),
            belongsToProject = groupProperties.getOrElse(OntologyConstants.KnoraBase.BelongsToProject, throw InconsistentTriplestoreDataException(s"Group $groupIri has no project attached")),
            status = groupProperties(OntologyConstants.KnoraBase.Status).toBoolean,
            hasSelfJoinEnabled = groupProperties(OntologyConstants.KnoraBase.HasSelfJoinEnabled).toBoolean)
    }


}
