/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.salsah.browser

import org.openqa.selenium.WebElement

/**
  * Tests the SALSAH web interface using Selenium.
  */
class ResourceCreationSpec extends SalsahSpec {
    /*

       We use the Selenium API directly instead of the ScalaTest wrapper, because the Selenium API is more
       powerful and more efficient.

       See https://selenium.googlecode.com/git/docs/api/java/index.html?org/openqa/selenium/WebDriver.html
       for more documentation.

     */
    private val headless = settings.headless
    private val pageUrl = s"http://${settings.hostName}:${settings.httpPort}/index.html"
    private val page = new SalsahPage(pageUrl, headless)

    // How long to wait for results obtained using the 'eventually' function
    implicit private val patienceConfig = page.patienceConfig



    private val rdfDataObjectsJsonList: String =
        """
            [
                {"path": "_test_data/all_data/incunabula-data.ttl", "name": "http://www.knora.org/data/incunabula"},
                {"path": "_test_data/demo_data/images-demo-data.ttl", "name": "http://www.knora.org/data/images"},
                {"path": "_test_data/all_data/anything-data.ttl", "name": "http://www.knora.org/data/anything"},
                {"path": "_test_data/all_data/biblio-data.ttl", "name": "http://www.knora.org/data/biblio"}
            ]
        """

    private val anythingUserEmail = "anything.user01@example.org"
    private val anythingUserFullName = "Anything User01"

    private val imagesUserEmail = "user02.user@example.com"
    private val imagesUserFullName = "User02 User"

    private val multiUserEmail = "multi.user@example.com"
    private val multiUserFullName = "Multi User"

    private val testPassword = "test"

    private val anythingProjectIri = "http://data.knora.org/projects/anything"
    private val incunabulaProjectIri = "http://data.knora.org/projects/77275339"

    // In order to run these tests, start `webapi` using the option `allowResetTriplestoreContentOperationOverHTTP`

    "The SALSAH home page" should {
        "load test data" in {
            loadTestData(rdfDataObjectsJsonList)
        }

        "have the correct title" in {
            page.open()
            page.getPageTitle should be("System for Annotation and Linkage of Sources in Arts and Humanities")

        }

        "log in as anything user" in {

            page.open()
            page.doLogin(email = anythingUserEmail, password = testPassword, fullName = anythingUserFullName)
            page.doLogout()

        }

        "create a resource of type images:person" in {

            page.open()

            page.doLogin(email = imagesUserEmail, password = testPassword, fullName = imagesUserFullName)

            page.clickAddResourceButton()

            val restypes = page.selectRestype("http://www.knora.org/ontology/images#person")

            val label: WebElement = page.getFormFieldByName("__LABEL__")

            label.sendKeys("Robin Hood")

            val firstname: WebElement = page.getFormFieldByName("http://www.knora.org/ontology/images#firstname")

            firstname.sendKeys("Robin")

            val familyname: WebElement = page.getFormFieldByName("http://www.knora.org/ontology/images#lastname")

            familyname.sendKeys("Hood")

            val address: WebElement = page.getFormFieldByName("http://www.knora.org/ontology/images#address")

            address.sendKeys("Sherwood Forest")

            page.clickSaveButtonForResourceCreationForm()

            val window = page.getWindow(1)

            page.doLogout()

        }

        "create two resources of type anything:thing in two different projects as the multi-project user" in {

            page.open()

            page.doLogin(email = multiUserEmail, password = testPassword, fullName = multiUserFullName)

            page.selectProject(anythingProjectIri)

            page.clickAddResourceButton()

            page.selectVocabulary("0") // select all

            page.selectRestype("http://www.knora.org/ontology/anything#Thing")

            val resource1Label: WebElement = page.getFormFieldByName("__LABEL__")

            resource1Label.sendKeys("Testding")

            val resource1FloatVal = page.getFormFieldByName("http://www.knora.org/ontology/anything#hasDecimal")

            resource1FloatVal.sendKeys("5.3")

            val resource1TextVal = page.getFormFieldByName("http://www.knora.org/ontology/anything#hasText")

            resource1TextVal.sendKeys("Dies ist ein Test")

            page.clickSaveButtonForResourceCreationForm()

            val resource1Window = page.getWindow(1)

            page.closeWindow(resource1Window)

            page.selectProject(incunabulaProjectIri)

            page.clickAddResourceButton()

            page.selectVocabulary("0") // select all

            page.selectRestype("http://www.knora.org/ontology/anything#Thing")

            val resource2Label: WebElement = page.getFormFieldByName("__LABEL__")

            resource2Label.sendKeys("ein zweites Testding")

            val resource2FloatVal = page.getFormFieldByName("http://www.knora.org/ontology/anything#hasDecimal")

            resource2FloatVal.sendKeys("5.7")

            val resource2TextVal = page.getFormFieldByName("http://www.knora.org/ontology/anything#hasText")

            resource2TextVal.sendKeys("Dies ist auch ein Test")

            page.clickSaveButtonForResourceCreationForm()

            page.doLogout()

        }

        "close the browser" in {
            page.quit()
        }
    }
}
