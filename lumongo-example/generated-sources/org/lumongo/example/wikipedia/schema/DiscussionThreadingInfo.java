
package org.lumongo.example.wikipedia.schema;

import java.math.BigInteger;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for DiscussionThreadingInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="DiscussionThreadingInfo">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="ThreadSubject" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="ThreadParent" type="{http://www.w3.org/2001/XMLSchema}positiveInteger"/>
 *         &lt;element name="ThreadAncestor" type="{http://www.w3.org/2001/XMLSchema}positiveInteger"/>
 *         &lt;element name="ThreadPage" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="ThreadID" type="{http://www.w3.org/2001/XMLSchema}positiveInteger"/>
 *         &lt;element name="ThreadAuthor" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="ThreadEditStatus" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="ThreadType" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "DiscussionThreadingInfo", propOrder = {
    "threadSubject",
    "threadParent",
    "threadAncestor",
    "threadPage",
    "threadID",
    "threadAuthor",
    "threadEditStatus",
    "threadType"
})
public class DiscussionThreadingInfo {

    @XmlElement(name = "ThreadSubject", required = true)
    protected String threadSubject;
    @XmlElement(name = "ThreadParent", required = true)
    @XmlSchemaType(name = "positiveInteger")
    protected BigInteger threadParent;
    @XmlElement(name = "ThreadAncestor", required = true)
    @XmlSchemaType(name = "positiveInteger")
    protected BigInteger threadAncestor;
    @XmlElement(name = "ThreadPage", required = true)
    protected String threadPage;
    @XmlElement(name = "ThreadID", required = true)
    @XmlSchemaType(name = "positiveInteger")
    protected BigInteger threadID;
    @XmlElement(name = "ThreadAuthor", required = true)
    protected String threadAuthor;
    @XmlElement(name = "ThreadEditStatus", required = true)
    protected String threadEditStatus;
    @XmlElement(name = "ThreadType", required = true)
    protected String threadType;

    /**
     * Gets the value of the threadSubject property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getThreadSubject() {
        return threadSubject;
    }

    /**
     * Sets the value of the threadSubject property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setThreadSubject(String value) {
        this.threadSubject = value;
    }

    /**
     * Gets the value of the threadParent property.
     * 
     * @return
     *     possible object is
     *     {@link BigInteger }
     *     
     */
    public BigInteger getThreadParent() {
        return threadParent;
    }

    /**
     * Sets the value of the threadParent property.
     * 
     * @param value
     *     allowed object is
     *     {@link BigInteger }
     *     
     */
    public void setThreadParent(BigInteger value) {
        this.threadParent = value;
    }

    /**
     * Gets the value of the threadAncestor property.
     * 
     * @return
     *     possible object is
     *     {@link BigInteger }
     *     
     */
    public BigInteger getThreadAncestor() {
        return threadAncestor;
    }

    /**
     * Sets the value of the threadAncestor property.
     * 
     * @param value
     *     allowed object is
     *     {@link BigInteger }
     *     
     */
    public void setThreadAncestor(BigInteger value) {
        this.threadAncestor = value;
    }

    /**
     * Gets the value of the threadPage property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getThreadPage() {
        return threadPage;
    }

    /**
     * Sets the value of the threadPage property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setThreadPage(String value) {
        this.threadPage = value;
    }

    /**
     * Gets the value of the threadID property.
     * 
     * @return
     *     possible object is
     *     {@link BigInteger }
     *     
     */
    public BigInteger getThreadID() {
        return threadID;
    }

    /**
     * Sets the value of the threadID property.
     * 
     * @param value
     *     allowed object is
     *     {@link BigInteger }
     *     
     */
    public void setThreadID(BigInteger value) {
        this.threadID = value;
    }

    /**
     * Gets the value of the threadAuthor property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getThreadAuthor() {
        return threadAuthor;
    }

    /**
     * Sets the value of the threadAuthor property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setThreadAuthor(String value) {
        this.threadAuthor = value;
    }

    /**
     * Gets the value of the threadEditStatus property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getThreadEditStatus() {
        return threadEditStatus;
    }

    /**
     * Sets the value of the threadEditStatus property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setThreadEditStatus(String value) {
        this.threadEditStatus = value;
    }

    /**
     * Gets the value of the threadType property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getThreadType() {
        return threadType;
    }

    /**
     * Sets the value of the threadType property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setThreadType(String value) {
        this.threadType = value;
    }

}
