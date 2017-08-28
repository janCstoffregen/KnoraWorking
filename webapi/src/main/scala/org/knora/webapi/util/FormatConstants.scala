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

package org.knora.webapi.util

/**
  * Constants for formatting data.
  */
object FormatConstants {
    // A non-printing delimiter character, Unicode INFORMATION SEPARATOR ONE, that should never occur in data.
    val INFORMATION_SEPARATOR_ONE = '\u001F'

    // A non-printing delimiter character, Unicode INFORMATION SEPARATOR TWO, that should never occur in data.
    val INFORMATION_SEPARATOR_TWO = '\u001E'

    // A non-printing delimiter character, Unicode INFORMATION SEPARATOR TWO, that should never occur in data.
    val INFORMATION_SEPARATOR_THREE = '\u001D'

    // A non-printing delimiter character, Unicode INFORMATION SEPARATOR TWO, that should never occur in data.
    val INFORMATION_SEPARATOR_FOUR = '\u001C'

    // a separator to be inserted in the XML to separate nodes from one another
    // this separator is only used temporarily while XML is being processed
    val PARAGRAPH_SEPARATOR = '\u2029'

    // Control sequences for changing text colour in terminals.
    val ANSI_RED = "\u001B[31m"
    val ANSI_GREEN = "\u001B[32m"
    val ANSI_YELLOW = "\u001B[33m"
    val ANSI_RESET = "\u001B[0m"
}
