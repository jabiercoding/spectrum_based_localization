package spectrum.utils;

import java.io.File;
import java.util.List;
import java.util.Map;

import utils.FileUtils;

public class HTMLReportUtils {

	/**
	 * Create html report
	 * @param outputFolder
	 * @param mapScenarioMetricsFile
	 */
	public static void create(File outputFolder, Map<String, File> mapScenarioMetricsFile) {
		
		// Copy the report template
		File report = new File(outputFolder, "report/report.html");
		FileUtils.copyFile(new File("reportTemplate/report.html"), report);
		FileUtils.copyFile(new File("reportTemplate/libs_js/Chart.bundle.js"), new File(outputFolder, "report/libs_js/Chart.bundle.js"));
		FileUtils.copyFile(new File("reportTemplate/libs_js/utils.js"), new File(outputFolder, "report/libs_js/utils.js"));
		
		// Get the positions of the parts to replace from the template
		List<String> lines = FileUtils.getLinesOfFile(report);
		int indexOfScenarioLabels = 0;
		int indexOfPrecision = 0;
		int indexOfRecall = 0;
		int indexOfF1 = 0;
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i).trim();
			if (line.startsWith("labels:")) {
				indexOfScenarioLabels = i;
			} else if (line.startsWith("label: \"Precision\"")) {
				indexOfPrecision = i;
			} else if (line.startsWith("label: \"Recall\"")) {
				indexOfRecall = i;
			} else if (line.startsWith("label: \"F1\"")) {
				indexOfF1 = i;
			}
		}
		
		// Create the new lines
		StringBuffer scenariosLine = new StringBuffer();
		StringBuffer precisionLine = new StringBuffer();
		StringBuffer recallLine = new StringBuffer();
		StringBuffer f1Line = new StringBuffer();
		
		scenariosLine.append("labels: [");
		for (String scenario: mapScenarioMetricsFile.keySet()) {
			// short it
			scenario = scenario.replace("Scenario", "");
			scenario = scenario.replace("Variants", "");
			scenario = scenario.replace("Variant", "");
			scenariosLine.append("\"" + scenario + "\",");
		}
		// remove last comma
		scenariosLine.substring(0, scenariosLine.length()-2);
		scenariosLine.append("],\n");
		
		lines.remove(indexOfScenarioLabels);
		lines.add(indexOfScenarioLabels, scenariosLine.toString());
		
		precisionLine.append("label: \"Precision\", data: [");
		recallLine.append("label: \"Recall\", data: [");
		f1Line.append("label: \"F1\", data: [");
		
		for (String scenario: mapScenarioMetricsFile.keySet()) {
			File resultsFile = mapScenarioMetricsFile.get(scenario);
			List<String> resultsLines = FileUtils.getLinesOfFile(resultsFile);
			for (String resultLine: resultsLines) {
				if (resultLine.startsWith("Average,")) {
					String[] splitResultLine = resultLine.split(",");
					precisionLine.append(splitResultLine[1] + ",");
					recallLine.append(splitResultLine[2] + ",");
					f1Line.append(splitResultLine[3] + ",");
				}
			}
		}
		
		// remove last comma
		precisionLine.substring(0, precisionLine.length()-2);
		precisionLine.append("]\n");
		recallLine.substring(0, recallLine.length()-2);
		recallLine.append("]\n");
		f1Line.substring(0, f1Line.length()-2);
		f1Line.append("]\n");
		
		lines.remove(indexOfPrecision);
		lines.add(indexOfPrecision, precisionLine.toString());
		lines.remove(indexOfRecall);
		lines.add(indexOfRecall, recallLine.toString());
		lines.remove(indexOfF1);
		lines.add(indexOfF1, f1Line.toString());
				
		// Save the report
		StringBuffer newContent = new StringBuffer();
		for (String line: lines) {
			newContent.append(line);
			newContent.append("\n");
		}
		try {
			FileUtils.writeFile(report, newContent.toString());
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
	
}
