/* 
 * Copyright (c) 2016, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

/*
 * Class for generating code coverage reportfor a given ApexUnit run
 * 
 * @author adarsh.ramakrishna@salesforce.com
 */ 
 

package com.sforce.cd.apexUnit.report;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Element;

import com.sforce.cd.apexUnit.ApexUnitUtils;
import com.sforce.cd.apexUnit.arguments.CommandLineArguments;
import com.sforce.cd.apexUnit.client.fileReader.ApexManifestFileReader;
import com.sforce.cd.apexUnit.client.testEngine.TestStatusPollerAndResultHandler;
import com.sforce.cd.apexUnit.client.utils.ApexClassFetcherUtils;

public class ApexCodeCoverageReportGenerator {

	public static void generateHTMLReport(ApexClassCodeCoverageBean[] apexClassCodeCoverageBeans) {
		// Preparing the table:
		StringBuilder htmlBuilder = new StringBuilder();
		htmlBuilder.append("<html>");
		// String styleTagProperties =
		// "table { font-size: 18px; background-color: blue; color: orange;
		// text-align: center; }"
		// +
		// "body { font-family: \"Times New Roman\"; text-align: center;
		// font-size: 20px;}";
		// appendTag(htmlBuilder, "style", styleTagProperties, "");

		// Print a summary of the coverage for the team's apex classes and
		// triggers
		StringBuilder styleBuilder = new StringBuilder();
		// define all styles for the html tags here
		String styleBuilderString = "body {background-color:white;} " + "h1   {color:blue; font-size:300%}"
				+ "summary    {color:black; font-size:125%;}" + "header    {color:blue; font-size:200%;}"
				+ "th   {color:blue; font-size:125%; background-color:lightgrey;}";
		appendTag(styleBuilder, "style", styleBuilderString);
		htmlBuilder.append(styleBuilder);
		StringBuilder summaryHeader = new StringBuilder();

		String summaryHeaderString = "<b>ApexUnit Report</b>\n";
		appendTag(summaryHeader, "h1", "align = 'center'; font-size: 25px; ", summaryHeaderString);
		appendLineSpaces(summaryHeader, 2);
		htmlBuilder.append(summaryHeader);
		
		StringBuilder codeCoverageSummary = new StringBuilder();
		
		appendTag(codeCoverageSummary, "header", "Code Coverage Summary: *");
		appendLineSpaces(codeCoverageSummary, 2);
		String teamCodeCoverageSummaryString = " Team code coverage: "
				+ String.format("%.2f", ApexUnitCodeCoverageResults.teamCodeCoverage) + "%"
				+ "  [The customized team code coverage threshold was: "
				+ CommandLineArguments.getTeamCodeCoverageThreshold() + "%]";
		String orgWideCodeCoverageSummaryString = "<br/> Org wide code coverage: "
				+ String.format("%.2f", ApexUnitCodeCoverageResults.orgWideCodeCoverage) + "%"
				+ "  [The customized org wide code coverage threshold was: "
				+ CommandLineArguments.getOrgWideCodeCoverageThreshold() + "%]";
		if (ApexUnitCodeCoverageResults.teamCodeCoverage < CommandLineArguments.getTeamCodeCoverageThreshold()) {
			appendTag(codeCoverageSummary, "summary", "style=\"color:crimson\"", teamCodeCoverageSummaryString);
		} else {
			appendTag(codeCoverageSummary, "summary", teamCodeCoverageSummaryString);
		}
		if (ApexUnitCodeCoverageResults.orgWideCodeCoverage < CommandLineArguments.getOrgWideCodeCoverageThreshold()) {
			appendTag(codeCoverageSummary, "summary", "style=\"color:crimson\"", orgWideCodeCoverageSummaryString);
		} else {
			appendTag(codeCoverageSummary, "summary", orgWideCodeCoverageSummaryString);
		}
		appendLineSpaces(codeCoverageSummary, 2);

		htmlBuilder.append(codeCoverageSummary);

		StringBuilder apexTestExecutionSummary = new StringBuilder();
		appendTag(apexTestExecutionSummary, "header", "Test Execution Summary: ");
		appendLineSpaces(apexTestExecutionSummary, 2);
		int failureTestMethodsCount = 0;
		if (TestStatusPollerAndResultHandler.testFailures) {
			if (TestStatusPollerAndResultHandler.failedTestMethods != null
					&& !TestStatusPollerAndResultHandler.failedTestMethods.isEmpty())
				failureTestMethodsCount = TestStatusPollerAndResultHandler.failedTestMethods.size();
		}
		StringBuffer apexTestExecutionSummaryString = new StringBuffer(
				" Total test classes executed: " + TestStatusPollerAndResultHandler.totalTestClasses);
		if (TestStatusPollerAndResultHandler.totalTestClassesAborted > 0) {
			apexTestExecutionSummaryString.append("<br/>Total Apex test classes aborted: "
					+ TestStatusPollerAndResultHandler.totalTestClassesAborted);
		}
		apexTestExecutionSummaryString.append(
				"<br/> Total test methods executed: " + TestStatusPollerAndResultHandler.totalTestMethodsExecuted);
		apexTestExecutionSummaryString.append("<br/> Test method pass count: "
				+ (TestStatusPollerAndResultHandler.totalTestMethodsExecuted - failureTestMethodsCount));
		apexTestExecutionSummaryString.append("<br/> Test method fail count: " + failureTestMethodsCount);

		appendTag(apexTestExecutionSummary, "summary", apexTestExecutionSummaryString.toString());
		appendLineSpaces(apexTestExecutionSummary, 1);

		htmlBuilder.append(apexTestExecutionSummary);
		appendLineSpaces(htmlBuilder, 2);
		// provide link to the test report
		appendTag(htmlBuilder, "header", "Apex Test Report: ");
		appendLineSpaces(htmlBuilder, 2);
		String workingDir = System.getProperty("user.dir");
		String apexUnitTestReportPath = "";
		if (!workingDir.contains("jenkins")) {
			apexUnitTestReportPath = workingDir + System.getProperty("file.separator") + "ApexUnitReport.xml";
		} else {
			int lastIndexOfSlash = workingDir.lastIndexOf('/');
			String jobName = workingDir.substring(lastIndexOfSlash + 1);
			apexUnitTestReportPath = "https://jenkins.internal.salesforce.com/job/" + jobName
					+ "/lastCompletedBuild/testReport/";
		}
		appendTag(htmlBuilder, "a", "style=\"font-size:125%\"; href=" + apexUnitTestReportPath, "Detailed Test Report");
		appendLineSpaces(htmlBuilder, 2);

		appendTag(htmlBuilder, "header", "Detailed code coverage report: ");
		appendLineSpaces(htmlBuilder, 2);
		htmlBuilder.append("<body>");

		StringBuilder codeCoverageHTMLContent = new StringBuilder();
		if (apexClassCodeCoverageBeans != null) {
			// populate the header cells for the table
			codeCoverageHTMLContent.append("<header>");
			appendHeaderCell(codeCoverageHTMLContent, "", "Apex Class Name");
			appendHeaderCell(codeCoverageHTMLContent, "", "API Version");
			appendHeaderCell(codeCoverageHTMLContent, "", "Code Coverage %");
			appendHeaderCell(codeCoverageHTMLContent, "", "#Covered Lines");
			appendHeaderCell(codeCoverageHTMLContent, "", "#Uncovered Lines");
			appendHeaderCell(codeCoverageHTMLContent, "", "Covered Lines");
			appendHeaderCell(codeCoverageHTMLContent, "", "Uncovered Lines");
			appendHeaderCell(codeCoverageHTMLContent, "", "Length Without Comments(Bytes)");
			codeCoverageHTMLContent.append("</header>");
			appendTag(codeCoverageHTMLContent, "tr", "");
			codeCoverageHTMLContent.append("<lowcc>");
			// populate the data cells for the table
			for (ApexClassCodeCoverageBean apexClassCodeCoverageBean : apexClassCodeCoverageBeans) {
				String codeCoverageStyle = "";
				if (apexClassCodeCoverageBean.getCoveragePercentage() < CommandLineArguments
						.getTeamCodeCoverageThreshold()) {
					codeCoverageStyle = "align='Center' style=\"color:crimson\"";
				} else {
					codeCoverageStyle = "align='Center' style=\"color:green\"";
				}
				appendDataCell(codeCoverageHTMLContent, codeCoverageStyle,
						apexClassCodeCoverageBean.getApexClassName());
				appendDataCell(codeCoverageHTMLContent, codeCoverageStyle, apexClassCodeCoverageBean.getApiVersion());
				appendDataCell(codeCoverageHTMLContent, codeCoverageStyle,
						String.format("%.2f", apexClassCodeCoverageBean.getCoveragePercentage()) + "%");
				appendDataCell(codeCoverageHTMLContent, codeCoverageStyle,
						"" + apexClassCodeCoverageBean.getNumLinesCovered());
				appendDataCell(codeCoverageHTMLContent, codeCoverageStyle,
						"" + apexClassCodeCoverageBean.getNumLinesUncovered());
				appendDataCell(codeCoverageHTMLContent, codeCoverageStyle,
						populateListInAStringBuffer(apexClassCodeCoverageBean.getCoveredLinesList()));
				appendDataCell(codeCoverageHTMLContent, codeCoverageStyle,
						populateListInAStringBuffer(apexClassCodeCoverageBean.getUncoveredLinesList()));
				appendDataCell(codeCoverageHTMLContent, codeCoverageStyle,
						apexClassCodeCoverageBean.getLengthWithoutComments());
				appendTag(codeCoverageHTMLContent, "tr", "");
			}
			codeCoverageHTMLContent.append("</lowcc>");
		}
		htmlBuilder.append("<table border='1'>");
		htmlBuilder.append(codeCoverageHTMLContent);
		htmlBuilder.append("</table>");

		// list out the duplicate entries(if any)
		if (ApexClassFetcherUtils.duplicateApexClassMap != null
				&& ApexClassFetcherUtils.duplicateApexClassMap.size() > 0) {
			StringBuilder duplicateApexClassesHTMLContent = new StringBuilder();
			appendLineSpaces(duplicateApexClassesHTMLContent, 2);
			duplicateApexClassesHTMLContent.append("<table border='1'>");
			appendHeaderCell(duplicateApexClassesHTMLContent, "",
					"Duplicate Apex Class Names Across Manifest Files And Regular Expressions");
			for (String duplicateEntry : ApexClassFetcherUtils.duplicateApexClassMap.values()) {
				appendTag(duplicateApexClassesHTMLContent, "tr", "");
				appendDataCell(duplicateApexClassesHTMLContent, "", duplicateEntry);
			}
			duplicateApexClassesHTMLContent.append("</table>");
			htmlBuilder.append(duplicateApexClassesHTMLContent);
		}

		// list out the non existant class entries(if any)
		if (ApexManifestFileReader.nonExistantApexClassEntries != null
				&& ApexManifestFileReader.nonExistantApexClassEntries.size() > 0) {
			StringBuilder nonExistantApexClassesHTMLContent = new StringBuilder();
			appendLineSpaces(nonExistantApexClassesHTMLContent, 2);
			nonExistantApexClassesHTMLContent.append("<table border='1'>");
			appendHeaderCell(nonExistantApexClassesHTMLContent, "",
					"Invalid/Non-existant Apex Class Names Across Manifest Files And Regular Expressions");
			for (String invalidEntry : ApexManifestFileReader.nonExistantApexClassEntries) {
				appendTag(nonExistantApexClassesHTMLContent, "tr", "");
				appendDataCell(nonExistantApexClassesHTMLContent, "", invalidEntry);
			}
			nonExistantApexClassesHTMLContent.append("</table>");
			htmlBuilder.append(nonExistantApexClassesHTMLContent);
		}
		appendLineSpaces(htmlBuilder, 2);
		appendTag(htmlBuilder, "a",
				"href=" + "http://www.salesforce.com/us/developer/docs/apexcode/Content/apex_code_coverage_best_pract.htm",
				"Apex_Code_Coverage_Best_Practices");
		htmlBuilder.append(
				"<br/> <br/> <i>* Code coverage is calculated by dividing the number of unique Apex code lines executed during your test method execution by the total number of Apex code lines in all of your trigger and classes. <br/>(Note: these numbers do not include lines of code within your testMethods)</i>");
		htmlBuilder.append("</body>");
		htmlBuilder.append("</html>");

		createHTMLReport(htmlBuilder.toString());
	}

	public static void generateCoberturaCoverageReport(ApexClassCodeCoverageBean[] apexClassCodeCoverageBeans) {
		try (FileWriter fw = new FileWriter("Cobertura4Apex.xml")) {
			Document doc = createCoberturaCoverageDocument();
			
			generateCoberturaCoverageReport(doc, apexClassCodeCoverageBeans);
			
			writeCoberturaCoverageReport(doc, fw);
		}
		catch (IOException e) {
			ApexUnitUtils
				.shutDownWithDebugLog(e, "IOException encountered during creation of Cobertura Coverage Report file Cobertura4Apex.xml");
		}
		catch (ParserConfigurationException e) {
			ApexUnitUtils
				.shutDownWithDebugLog(e, "ParserConfigurationException encountered during creation of Cobertura Coverage Report XML document");
		}
		catch (TransformerException e) {
			ApexUnitUtils
				.shutDownWithDebugLog(e, "TransformerException encountered during generation of Cobertura Coverage Report output");
		}
		
	}
	
	private static Document createCoberturaCoverageDocument() throws ParserConfigurationException {
		DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
		DocumentType documentType = documentBuilder.getDOMImplementation().createDocumentType("coverage", null, "http://cobertura.sourceforge.net/xml/coverage-03.dtd");
		Document document = documentBuilder.getDOMImplementation().createDocument(null, "coverage", documentType);
		
		return document;
	}
	
	private static void generateCoberturaCoverageReport(Document doc, ApexClassCodeCoverageBean[] apexClassCodeCoverageBeans) {
		Element coverageElement = doc.getDocumentElement();
		coverageElement.setAttribute("timestamp", Long.toString(System.currentTimeMillis()));
		coverageElement.setAttribute("version", "0.0");
		coverageElement.setAttribute("branch-rate", "0.0");
		coverageElement.setAttribute("line-rate", "0.0");
		
		Element sourcesElement = doc.createElement("sources");
		coverageElement.appendChild(sourcesElement);
		
		Element sourceElement = doc.createElement("source");
		sourceElement.appendChild(doc.createTextNode("Salesforce.com"));
		sourcesElement.appendChild(sourceElement);
		
		Element packagesElement = doc.createElement("packages");
		coverageElement.appendChild(packagesElement);
		
		Element packageElement = doc.createElement("package");
		packageElement.setAttribute("name", "Bayer Veeva");
		packageElement.setAttribute("complexity", "0.0");
		packageElement.setAttribute("branch-rate", "0.0");
		packagesElement.appendChild(packageElement);
		
		Element classesElement = doc.createElement("classes");
		packageElement.appendChild(classesElement);
		
		int totalLinesCovered = 0;
		int totalLinesUncovered = 0;
		
		for (ApexClassCodeCoverageBean apexClassCodeCoverageBean : apexClassCodeCoverageBeans) {
			totalLinesCovered += apexClassCodeCoverageBean.getNumLinesCovered();
			totalLinesUncovered += apexClassCodeCoverageBean.getNumLinesUncovered();
			
			classesElement.appendChild(addClassCoverage(apexClassCodeCoverageBean, doc));
		}
		
		int totalLines = totalLinesCovered + totalLinesUncovered;
		
		double coveragePercentage = totalLines > 0 ? 100.0 * totalLinesCovered / totalLines : 100.0;
		
		packageElement.setAttribute("line-rate", Double.toString(coveragePercentage));
	}
	
	private static void writeCoberturaCoverageReport(Document doc, Writer wr) throws TransformerException {
		TransformerFactory transformerFactory = TransformerFactory.newInstance();
		Transformer transformer = transformerFactory.newTransformer();
		transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		transformer.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, doc.getDoctype().getSystemId());
		
		transformer.transform(new DOMSource(doc), new StreamResult(wr));
	}
	
	private static Element addClassCoverage(ApexClassCodeCoverageBean apexClassCodeCoverageBean, final Document doc) {
		Element classElement = doc.createElement("class");
		
		classElement.setAttribute("name", apexClassCodeCoverageBean.getApexClassName());
		classElement.setAttribute("filename", apexClassCodeCoverageBean.getApexClassName());
		classElement.setAttribute("complexity", "0.0");
		classElement.setAttribute("branch-rate", "0.0");
		classElement.setAttribute("line-rate", Double.toString(apexClassCodeCoverageBean.getCoveragePercentage()));
		
		Element methodsElement = doc.createElement("methods");
		classElement.appendChild(methodsElement);
		
		Element linesElement = doc.createElement("lines");
		classElement.appendChild(linesElement);
		
		addLinesCoverage(apexClassCodeCoverageBean, linesElement);
		
		return classElement;
	}
	
	private static void addLinesCoverage(ApexClassCodeCoverageBean apexClassCodeCoverageBean, Element linesElement) {
		Document doc = linesElement.getOwnerDocument();
		
		for (Long location : apexClassCodeCoverageBean.getUncoveredLinesList()) {
			Element lineElement = doc.createElement("line");
			lineElement.setAttribute("number", Long.toString(location));
			lineElement.setAttribute("hits", "0");
			linesElement.appendChild(lineElement);
		}
		
		for (Long location : apexClassCodeCoverageBean.getCoveredLinesList()) {
			Element lineElement = doc.createElement("line");
			lineElement.setAttribute("number", Long.toString(location));
			lineElement.setAttribute("hits", "1");
			linesElement.appendChild(lineElement);
		}
	}

	private static void createHTMLReport(String htmlBuffer) {

		File tmpFile = null;
		FileOutputStream tmpOut = null;
		String workingDir = System.getProperty("user.dir") + System.getProperty("file.separator") + "Report";
		File dir = new File(workingDir);
		dir.mkdirs();
		tmpFile = new File(dir, "ApexUnitReport.html");
		byte[] reportAsBytes;
		try {
			tmpOut = new FileOutputStream(tmpFile);
			reportAsBytes = htmlBuffer.getBytes("UTF-8");
			tmpOut.write(reportAsBytes);
			tmpOut.close();
		} catch (UnsupportedEncodingException e) {
			ApexUnitUtils
					.shutDownWithDebugLog(e, "UnsupportedEncodingException encountered while creating the HTML report");
		} catch (FileNotFoundException e) {
			ApexUnitUtils
					.shutDownWithDebugLog(e, "FileNotFoundException encountered while writing the HTML report to ApexUnitReport.html");
		} catch (IOException e) {
			ApexUnitUtils
					.shutDownWithDebugLog(e, "IOException encountered while writing the HTML report to ApexUnitReport.html");
		}
	}

	private static String populateListInAStringBuffer(List<Long> listWithValues) {
		StringBuffer processedListAsStrBuf = new StringBuffer("");
		int i = 0;
		if (listWithValues != null) {
			for (Long value : listWithValues) {
				i++;
				processedListAsStrBuf.append(value);
				processedListAsStrBuf.append(",");
				if (i >= 10) {
					processedListAsStrBuf.append("\n");
					i = 0;
				}
			}
			return processedListAsStrBuf.substring(0, processedListAsStrBuf.length() - 1);
		} else {
			return "-";
		}
	}

	private static void appendTag(StringBuilder sb, String tag, String tagProperties, String contents) {
		sb.append('<').append(tag).append(" " + tagProperties).append('>');
		sb.append(contents);
		sb.append("</").append(tag).append('>');
	}

	private static void appendTag(StringBuilder sb, String tag, String contents) {
		appendTag(sb, tag, "", contents);
	}

	private static void appendDataCell(StringBuilder sb, String tagProperties, String contents) {
		appendTag(sb, "td", tagProperties, contents);
	}

	private static void appendHeaderCell(StringBuilder sb, String tagProperties, String contents) {
		appendTag(sb, "th", tagProperties, contents);
	}

	private static void appendLineSpaces(StringBuilder sb, int numberOfLines) {
		for (int i = 1; i < numberOfLines; i++) {
			appendTag(sb, "br", "");
		}
	}
}
