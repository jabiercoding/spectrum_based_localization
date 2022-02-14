package spectrum.utils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import utils.FileUtils;

public class HTMLReportUtils {

	/**
	 * Create html report
	 * 
	 * @param outputFolder
	 * @param mapScenarioMetricsFile
	 * @param features,              use null for all
	 */
	public static void create(File outputFolder, Map<String, File> mapScenarioMetricsFile, List<String> features) {
		// Copy the report template
		File report = new File(outputFolder, "report/report.html");
		FileUtils.copyFile(new File("reportTemplate/report.html"), report);
		FileUtils.copyFile(new File("reportTemplate/libs_js/Chart.bundle.js"),
				new File(outputFolder, "report/libs_js/Chart.bundle.js"));
		FileUtils.copyFile(new File("reportTemplate/libs_js/utils.js"),
				new File(outputFolder, "report/libs_js/utils.js"));

		// Get the positions of the parts to replace from the template
		List<String> lines = FileUtils.getLinesOfFile(report);
		List<Integer> indexOfScenarioLabels = new ArrayList<Integer>();

		int indexOfFeatureList = 0;
		int indexOfPrecision = 0;
		int indexOfRecall = 0;
		int indexOfF1 = 0;
		int indexOfWithout = 0;
		int indexOfInexistent = 0;
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i).trim();
			if (line.startsWith("text: 'Feature location metrics'")) {
				indexOfFeatureList = i;
			} else if (line.startsWith("labels:")) {
				indexOfScenarioLabels.add(i);
			} else if (line.startsWith("label: \"Precision\"")) {
				indexOfPrecision = i;
			} else if (line.startsWith("label: \"Recall\"")) {
				indexOfRecall = i;
			} else if (line.startsWith("label: \"F1\"")) {
				indexOfF1 = i;
			} else if (line.startsWith("label: \"FeaturesWithoutRetrieved\"")) {
				indexOfWithout = i;
			} else if (line.startsWith("label: \"InexistentFeaturesRetrieved\"")) {
				indexOfInexistent = i;
			}
		}

		// Create the new lines
		StringBuffer scenariosLine = new StringBuffer();
		StringBuffer precisionLine = new StringBuffer();
		StringBuffer recallLine = new StringBuffer();
		StringBuffer f1Line = new StringBuffer();

		StringBuffer withoutLine = new StringBuffer();
		StringBuffer inexistentLine = new StringBuffer();

		scenariosLine.append("labels: [");
		for (String scenario : mapScenarioMetricsFile.keySet()) {
			// short it
			scenario = scenario.replace("Scenario", "");
			scenario = scenario.replace("Variants", "");
			scenario = scenario.replace("Variant", "");
			scenariosLine.append("\"" + scenario + "\",");
		}
		// remove last comma
		scenariosLine.substring(0, scenariosLine.length() - 2);
		scenariosLine.append("],\n");

		for (Integer i : indexOfScenarioLabels) {
			lines.remove(i.intValue());
			lines.add(i, scenariosLine.toString());
		}

		precisionLine.append("label: \"Precision\", data: [");
		recallLine.append("label: \"Recall\", data: [");
		f1Line.append("label: \"F1\", data: [");
		withoutLine.append("label: \"FeaturesWithoutRetrieved\", data: [");
		inexistentLine.append("label: \"InexistentFeaturesRetrieved\", data: [");

		for (String scenario : mapScenarioMetricsFile.keySet()) {
			File resultsFile = mapScenarioMetricsFile.get(scenario);
			List<String> resultsLines = FileUtils.getLinesOfFile(resultsFile);
			for (String resultLine : resultsLines) {
				if (resultLine.startsWith("Average,")) {
					String[] splitResultLine = resultLine.split(",");
					if (features == null) {
						precisionLine.append(splitResultLine[1] + ",");
						recallLine.append(splitResultLine[2] + ",");
						f1Line.append(splitResultLine[3] + ",");
					}
					withoutLine.append(splitResultLine[4] + ",");
					inexistentLine.append(splitResultLine[5] + ",");
				}
			}

			if (features != null) {
				double sumPrecision = 0;
				double sumRecall = 0;
				double sumF1 = 0;
				for (String resultLine : resultsLines) {
					String[] splitResultLine = resultLine.split(",");
					if (features.contains(splitResultLine[0])) {
						sumPrecision += Double.parseDouble(splitResultLine[1]);
						sumRecall += Double.parseDouble(splitResultLine[2]);
						sumF1 += Double.parseDouble(splitResultLine[3]);
					}
				}
				precisionLine.append(sumPrecision / features.size() + ",");
				recallLine.append(sumRecall / features.size() + ",");
				f1Line.append(sumF1 / features.size() + ",");
			}
		}

		// remove last comma
		precisionLine.substring(0, precisionLine.length() - 2);
		precisionLine.append("]\n");
		recallLine.substring(0, recallLine.length() - 2);
		recallLine.append("]\n");
		f1Line.substring(0, f1Line.length() - 2);
		f1Line.append("]\n");
		withoutLine.substring(0, withoutLine.length() - 2);
		withoutLine.append("]\n");
		inexistentLine.substring(0, inexistentLine.length() - 2);
		inexistentLine.append("]\n");

		// replace lines
		if (features != null) {
			lines.remove(indexOfFeatureList);
			lines.add(indexOfFeatureList, "text: 'Feature location metrics " + features + "'");
		}
		lines.remove(indexOfPrecision);
		lines.add(indexOfPrecision, precisionLine.toString());
		lines.remove(indexOfRecall);
		lines.add(indexOfRecall, recallLine.toString());
		lines.remove(indexOfF1);
		lines.add(indexOfF1, f1Line.toString());
		lines.remove(indexOfWithout);
		lines.add(indexOfWithout, withoutLine.toString());
		lines.remove(indexOfInexistent);
		lines.add(indexOfInexistent, inexistentLine.toString());

		// Save the report
		StringBuffer newContent = new StringBuffer();
		for (String line : lines) {
			newContent.append(line);
			newContent.append("\n");
		}
		try {
			FileUtils.writeFile(report, newContent.toString());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	
	
	/**
	 * Create html report naive results
	 * 
	 * @param outputFolder
	 * @param mapScenarioMetricsFile
	 * @param features,              use null for all
	 */
	public static void createReportNaiveResults(File outputFolder, Map<String, File> mapScenarioMetricsFile, List<String> features) {
		// Copy the report template
		File report = new File(outputFolder, "reportNaive/report.html");
		FileUtils.copyFile(new File("reportTemplate/report.html"), report);
		FileUtils.copyFile(new File("reportTemplate/libs_js/Chart.bundle.js"),
				new File(outputFolder, "report/libs_js/Chart.bundle.js"));
		FileUtils.copyFile(new File("reportTemplate/libs_js/utils.js"),
				new File(outputFolder, "report/libs_js/utils.js"));

		// Get the positions of the parts to replace from the template
		List<String> lines = FileUtils.getLinesOfFile(report);
		List<Integer> indexOfScenarioLabels = new ArrayList<Integer>();

		int indexOfFeatureList = 0;
		int indexOfPrecision = 0;
		int indexOfRecall = 0;
		int indexOfF1 = 0;
		int indexOfWithout = 0;
		int indexOfInexistent = 0;
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i).trim();
			if (line.startsWith("text: 'Feature location metrics'")) {
				indexOfFeatureList = i;
			} else if (line.startsWith("labels:")) {
				indexOfScenarioLabels.add(i);
			} else if (line.startsWith("label: \"Precision\"")) {
				indexOfPrecision = i;
			} else if (line.startsWith("label: \"Recall\"")) {
				indexOfRecall = i;
			} else if (line.startsWith("label: \"F1\"")) {
				indexOfF1 = i;
			} else if (line.startsWith("label: \"FeaturesWithoutRetrieved\"")) {
				indexOfWithout = i;
			} else if (line.startsWith("label: \"InexistentFeaturesRetrieved\"")) {
				indexOfInexistent = i;
			}
		}

		// Create the new lines
		StringBuffer scenariosLine = new StringBuffer();
		StringBuffer precisionLine = new StringBuffer();
		StringBuffer recallLine = new StringBuffer();
		StringBuffer f1Line = new StringBuffer();

		StringBuffer withoutLine = new StringBuffer();
		StringBuffer inexistentLine = new StringBuffer();

		scenariosLine.append("labels: [");
		for (String scenario : mapScenarioMetricsFile.keySet()) {
			// short it
			scenario = scenario.replace("Scenario", "");
			scenario = scenario.replace("Variants", "");
			scenario = scenario.replace("Variant", "");
			scenariosLine.append("\"" + scenario + "\",");
		}
		// remove last comma
		scenariosLine.substring(0, scenariosLine.length() - 2);
		scenariosLine.append("],\n");

		for (Integer i : indexOfScenarioLabels) {
			lines.remove(i.intValue());
			lines.add(i, scenariosLine.toString());
		}

		precisionLine.append("label: \"Precision\", data: [");
		recallLine.append("label: \"Recall\", data: [");
		f1Line.append("label: \"F1\", data: [");
		withoutLine.append("label: \"FeaturesWithoutRetrieved\", data: [");
		inexistentLine.append("label: \"InexistentFeaturesRetrieved\", data: [");

		for (String scenario : mapScenarioMetricsFile.keySet()) {
			File resultsFile = mapScenarioMetricsFile.get(scenario);
			List<String> resultsLines = FileUtils.getLinesOfFile(resultsFile);
			for (String resultLine : resultsLines) {
				if (resultLine.startsWith("Average,")) {
					String[] splitResultLine = resultLine.split(",");
					if (features == null) {
						precisionLine.append(splitResultLine[1] + ",");
						recallLine.append(splitResultLine[2] + ",");
						f1Line.append(splitResultLine[3] + ",");
					}
					withoutLine.append(splitResultLine[4] + ",");
					inexistentLine.append(splitResultLine[5] + ",");
				}
			}

			if (features != null) {
				double sumPrecision = 0;
				double sumRecall = 0;
				double sumF1 = 0;
				for (String resultLine : resultsLines) {
					String[] splitResultLine = resultLine.split(",");
					if (features.contains(splitResultLine[0])) {
						sumPrecision += Double.parseDouble(splitResultLine[1]);
						sumRecall += Double.parseDouble(splitResultLine[2]);
						sumF1 += Double.parseDouble(splitResultLine[3]);
					}
				}
				precisionLine.append(sumPrecision / features.size() + ",");
				recallLine.append(sumRecall / features.size() + ",");
				f1Line.append(sumF1 / features.size() + ",");
			}
		}

		// remove last comma
		precisionLine.substring(0, precisionLine.length() - 2);
		precisionLine.append("]\n");
		recallLine.substring(0, recallLine.length() - 2);
		recallLine.append("]\n");
		f1Line.substring(0, f1Line.length() - 2);
		f1Line.append("]\n");
		withoutLine.substring(0, withoutLine.length() - 2);
		withoutLine.append("]\n");
		inexistentLine.substring(0, inexistentLine.length() - 2);
		inexistentLine.append("]\n");

		// replace lines
		if (features != null) {
			lines.remove(indexOfFeatureList);
			lines.add(indexOfFeatureList, "text: 'Feature location metrics " + features + "'");
		}
		lines.remove(indexOfPrecision);
		lines.add(indexOfPrecision, precisionLine.toString());
		lines.remove(indexOfRecall);
		lines.add(indexOfRecall, recallLine.toString());
		lines.remove(indexOfF1);
		lines.add(indexOfF1, f1Line.toString());
		lines.remove(indexOfWithout);
		lines.add(indexOfWithout, withoutLine.toString());
		lines.remove(indexOfInexistent);
		lines.add(indexOfInexistent, inexistentLine.toString());

		// Save the report
		StringBuffer newContent = new StringBuffer();
		for (String line : lines) {
			newContent.append(line);
			newContent.append("\n");
		}
		try {
			FileUtils.writeFile(report, newContent.toString());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	
	
	/**
	 * Create html report naive results
	 * 
	 * @param outputFolder
	 * @param mapScenarioMetricsFile
	 */
	public static void createReportNaiveResults(File outputFolder, Map<String, File> mapScenarioMetricsFile) {
		// null means all features
		create(outputFolder, mapScenarioMetricsFile, null);
	}

	
	/**
	 * Create html report
	 * 
	 * @param outputFolder
	 * @param mapScenarioMetricsFile
	 */
	public static void create(File outputFolder, Map<String, File> mapScenarioMetricsFile) {
		// null means all features
		create(outputFolder, mapScenarioMetricsFile, null);
	}
}
