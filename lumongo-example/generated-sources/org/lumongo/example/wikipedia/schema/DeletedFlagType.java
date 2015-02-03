
package org.lumongo.example.wikipedia.schema;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for DeletedFlagType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="DeletedFlagType">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}NMTOKEN">
 *     &lt;enumeration value="deleted"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "DeletedFlagType")
@XmlEnum
public enum DeletedFlagType {

    @XmlEnumValue("deleted")
    DELETED("deleted");
    private final String value;

    DeletedFlagType(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static DeletedFlagType fromValue(String v) {
        for (DeletedFlagType c: DeletedFlagType.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
