
package org.lumongo.example.wikipedia.schema;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for PageType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="PageType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="title" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="ns" type="{http://www.w3.org/2001/XMLSchema}nonNegativeInteger"/>
 *         &lt;element name="id" type="{http://www.w3.org/2001/XMLSchema}positiveInteger"/>
 *         &lt;element name="redirect" type="{http://www.mediawiki.org/xml/export-0.8/}RedirectType" minOccurs="0"/>
 *         &lt;element name="restrictions" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;choice maxOccurs="unbounded" minOccurs="0">
 *           &lt;element name="revision" type="{http://www.mediawiki.org/xml/export-0.8/}RevisionType"/>
 *           &lt;element name="upload" type="{http://www.mediawiki.org/xml/export-0.8/}UploadType"/>
 *         &lt;/choice>
 *         &lt;element name="discussionthreadinginfo" type="{http://www.mediawiki.org/xml/export-0.8/}DiscussionThreadingInfo" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PageType", propOrder = {
    "title",
    "ns",
    "id",
    "redirect",
    "restrictions",
    "revisionOrUpload",
    "discussionthreadinginfo"
})
public class PageType {

    @XmlElement(required = true)
    protected String title;
    @XmlElement(required = true)
    @XmlSchemaType(name = "nonNegativeInteger")
    protected BigInteger ns;
    @XmlElement(required = true)
    @XmlSchemaType(name = "positiveInteger")
    protected BigInteger id;
    protected RedirectType redirect;
    protected String restrictions;
    @XmlElements({
        @XmlElement(name = "revision", type = RevisionType.class),
        @XmlElement(name = "upload", type = UploadType.class)
    })
    protected List<Object> revisionOrUpload;
    protected DiscussionThreadingInfo discussionthreadinginfo;

    /**
     * Gets the value of the title property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getTitle() {
        return title;
    }

    /**
     * Sets the value of the title property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setTitle(String value) {
        this.title = value;
    }

    /**
     * Gets the value of the ns property.
     * 
     * @return
     *     possible object is
     *     {@link BigInteger }
     *     
     */
    public BigInteger getNs() {
        return ns;
    }

    /**
     * Sets the value of the ns property.
     * 
     * @param value
     *     allowed object is
     *     {@link BigInteger }
     *     
     */
    public void setNs(BigInteger value) {
        this.ns = value;
    }

    /**
     * Gets the value of the id property.
     * 
     * @return
     *     possible object is
     *     {@link BigInteger }
     *     
     */
    public BigInteger getId() {
        return id;
    }

    /**
     * Sets the value of the id property.
     * 
     * @param value
     *     allowed object is
     *     {@link BigInteger }
     *     
     */
    public void setId(BigInteger value) {
        this.id = value;
    }

    /**
     * Gets the value of the redirect property.
     * 
     * @return
     *     possible object is
     *     {@link RedirectType }
     *     
     */
    public RedirectType getRedirect() {
        return redirect;
    }

    /**
     * Sets the value of the redirect property.
     * 
     * @param value
     *     allowed object is
     *     {@link RedirectType }
     *     
     */
    public void setRedirect(RedirectType value) {
        this.redirect = value;
    }

    /**
     * Gets the value of the restrictions property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getRestrictions() {
        return restrictions;
    }

    /**
     * Sets the value of the restrictions property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setRestrictions(String value) {
        this.restrictions = value;
    }

    /**
     * Gets the value of the revisionOrUpload property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the revisionOrUpload property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getRevisionOrUpload().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link RevisionType }
     * {@link UploadType }
     * 
     * 
     */
    public List<Object> getRevisionOrUpload() {
        if (revisionOrUpload == null) {
            revisionOrUpload = new ArrayList<Object>();
        }
        return this.revisionOrUpload;
    }

    /**
     * Gets the value of the discussionthreadinginfo property.
     * 
     * @return
     *     possible object is
     *     {@link DiscussionThreadingInfo }
     *     
     */
    public DiscussionThreadingInfo getDiscussionthreadinginfo() {
        return discussionthreadinginfo;
    }

    /**
     * Sets the value of the discussionthreadinginfo property.
     * 
     * @param value
     *     allowed object is
     *     {@link DiscussionThreadingInfo }
     *     
     */
    public void setDiscussionthreadinginfo(DiscussionThreadingInfo value) {
        this.discussionthreadinginfo = value;
    }

}