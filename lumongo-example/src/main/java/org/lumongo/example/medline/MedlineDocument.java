package org.lumongo.example.medline;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.lumongo.cluster.message.Lumongo.LMAnalyzer;
import org.lumongo.fields.annotations.DefaultSearch;
import org.lumongo.fields.annotations.Faceted;
import org.lumongo.fields.annotations.Indexed;
import org.lumongo.fields.annotations.Settings;
import org.lumongo.fields.annotations.UniqueId;

@Settings(
				indexName = "medline",
				numberOfSegments = 8,
				segmentFlushInterval = 6000,
				segmentCommitInterval = 24000)
public class MedlineDocument {
	
	@DefaultSearch
	@Indexed(
					analyzer = LMAnalyzer.STANDARD)
	private String title;
	
	@Indexed(
					analyzer = LMAnalyzer.STANDARD)
	private String journalTitle;
	
	@Indexed(
		analyzer = LMAnalyzer.STANDARD)
	private String abstractText;
	
	@Faceted
	@Indexed(
		analyzer = LMAnalyzer.NUMERIC_LONG)
	private Date publicationDate;
	
	@Indexed(
					analyzer = LMAnalyzer.LC_KEYWORD)
	private String journalVolume;
	
	@Indexed(
		analyzer = LMAnalyzer.LC_KEYWORD)
	private String journalIssue;
	
	@Faceted
	@Indexed(
		analyzer = LMAnalyzer.LC_KEYWORD)
	private String journalCountry;
	
	@Faceted
	@Indexed(
					analyzer = LMAnalyzer.LC_KEYWORD)
	private String issn;
	
	@Indexed(
		analyzer = LMAnalyzer.STANDARD)
	private List<String> authors;
	
	@Faceted
	@Indexed(
					analyzer = LMAnalyzer.LC_KEYWORD)
	private List<String> authorsExact;
	
	@UniqueId
	private String pmid;
	
	public String getPmid() {
		return pmid;
	}
	
	public void setPmid(String pmid) {
		this.pmid = pmid;
	}
	
	public String getTitle() {
		return title;
	}
	
	public void setTitle(String title) {
		this.title = title;
	}
	
	public String getAbstractText() {
		return abstractText;
	}
	
	public void setAbstractText(String abstractText) {
		this.abstractText = abstractText;
	}
	
	public String getIssn() {
		return issn;
	}
	
	public void setIssn(String issn) {
		this.issn = issn;
	}
	
	public String getJournalTitle() {
		return journalTitle;
	}
	
	public void setJournalTitle(String journalTitle) {
		this.journalTitle = journalTitle;
	}
	
	public Date getPublicationDate() {
		return publicationDate;
	}
	
	public void setPublicationDate(Date publicationDate) {
		this.publicationDate = publicationDate;
	}
	
	public String getJournalVolume() {
		return journalVolume;
	}
	
	public void setJournalVolume(String journalVolume) {
		this.journalVolume = journalVolume;
	}
	
	public String getJournalIssue() {
		return journalIssue;
	}
	
	public void setJournalIssue(String journalIssue) {
		this.journalIssue = journalIssue;
	}
	
	public String getJournalCountry() {
		return journalCountry;
	}
	
	public void setJournalCountry(String journalCountry) {
		this.journalCountry = journalCountry;
	}
	
	public void addAuthor(String author) {
		if (this.authors == null) {
			this.authors = new ArrayList<String>();
		}
		if (this.authorsExact == null) {
			this.authorsExact = new ArrayList<String>();
		}
		
		this.authors.add(author);
		this.authorsExact.add(author);
		
	}
	
	@Override
	public String toString() {
		return "Document [title=" + title + ", journalTitle=" + journalTitle + ", abstractText=" + abstractText + ", publicationDate=" + publicationDate
						+ ", journalVolume=" + journalVolume + ", journalIssue=" + journalIssue + ", journalCountry=" + journalCountry + ", issn=" + issn
						+ ", authors=" + authors + ", authorsExact=" + authorsExact + ", pmid=" + pmid + "]\n";
	}
	
}
