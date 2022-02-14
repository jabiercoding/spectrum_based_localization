package spectrum.utils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import metricsCalculation.MetricsCalculation;
import utils.FileUtils;

public class TypeLevelMetricsCalculation {
	

	private static final String EXTENSION = ".txt";

	static int retrievedInexistentFeature_counter;
	static double failedToRetrieve_counter;
	
	/**
	 * read the content of the ground truth files and create the type, i.e., class level result files
	 * 
	 * @param groundTruthFolder    containing txt files with the ground truth values
	 * @param resultsFolder containing txt files with the retrieved values
	 */
	public static String getResults(File groundTruthFolder, File resultsFolder) {
		StringBuilder resultsContent = new StringBuilder();
		resultsContent.append("Name,Precision,Recall,FScore,FeaturesWithoutRetrieved,InexistentFeaturesRetrieved\n");
		double precisionAvg = 0;
		failedToRetrieve_counter = 0;
		double recallAvg = 0;
		double f1measureAvg = 0;
		double numberOfActualFiles = 0;
		// Go through the ground-truth folder
		for (File f : groundTruthFolder.listFiles()) {
			// be sure that it is a correct file
			if (f.getName().endsWith(EXTENSION)) {
				numberOfActualFiles++;
				List<String> actualLines = FileUtils.getLinesOfFile(f);
				if (!actualLines.isEmpty()) {
					ArrayList<String> linesGTTypeLevel = new ArrayList<>();
					for (String line : actualLines) {
						if(line.contains(" ")){
							line=line.substring(0, line.indexOf(" "));
						}
						if(!linesGTTypeLevel.contains(line))
							linesGTTypeLevel.add(line);
					}
					
					List<String> retrievedLines = null;
					ArrayList<String> linesResultsTypeLevel = new ArrayList<>();
					// get its counterpart in the results folder
					File f2 = new File(resultsFolder, f.getName());
					if (f2.exists()) {
						retrievedLines = FileUtils.getLinesOfFile(f2);
						for (String line : retrievedLines) {
							if(line.contains(" ")){
								line=line.substring(0, line.indexOf(" "));
							}
							if(!linesResultsTypeLevel.contains(line))
								linesResultsTypeLevel.add(line);
						}
					} else {
						// no file was created so it did not find anything
						linesResultsTypeLevel = new ArrayList<String>();
					}

					// Calculate metrics
					double precision = MetricsCalculation.getPrecision(linesGTTypeLevel, linesResultsTypeLevel);
					double recall = MetricsCalculation.getRecall(linesGTTypeLevel, linesResultsTypeLevel);
					double f1measure = MetricsCalculation.getF1(precision, recall);

					// Append the row to the results file
					// get the name by removing the file extension
					String name = f.getName().substring(0, f.getName().length() - EXTENSION.length());
					resultsContent.append(name + ",");

					// precision
					if (Double.isNaN(precision)) {
						resultsContent.append("0,");
					} else {
						precisionAvg += precision;
						resultsContent.append(precision + ",");
					}

					// recall
					if (Double.isNaN(recall)) {
						resultsContent.append("0,");
					} else {
						recallAvg += recall;
						resultsContent.append(recall + ",");
					}

					// f1score
					if (Double.isNaN(f1measure)) {
						resultsContent.append("0,");
					} else {
						f1measureAvg += f1measure;
						resultsContent.append(f1measure + ",");
					}

					// something retrieved or not
					if (linesResultsTypeLevel.isEmpty()) {
						failedToRetrieve_counter++;
						resultsContent.append("NothingRetrieved\n");
					} else {
						resultsContent.append("SomethingRetrieved\n");
					}
				}
			}
		}

		resultsContent.append("Average,");
		// precision avg.
		precisionAvg = precisionAvg / numberOfActualFiles;
		if (Double.isNaN(precisionAvg)) {
			resultsContent.append("0,");
		} else {
			resultsContent.append(precisionAvg + ",");
		}

		// recall avg.
		recallAvg = recallAvg / numberOfActualFiles;
		if (Double.isNaN(recallAvg)) {
			resultsContent.append("0,");
		} else {
			resultsContent.append(recallAvg + ",");
		}

		// f1score avg.
		f1measureAvg = f1measureAvg / numberOfActualFiles;
		if (Double.isNaN(f1measureAvg)) {
			resultsContent.append("0,");
		} else {
			resultsContent.append(f1measureAvg + ",");
		}

		// total failed to retrieve
		resultsContent.append(failedToRetrieve_counter + ",");

		// Check retrieved but inexistent in the actual folder
		StringBuilder inexistent = new StringBuilder();
		retrievedInexistentFeature_counter = 0;
		if (resultsFolder.listFiles() != null) {
			for (File f : resultsFolder.listFiles()) {

				File fIngroundTruthFolder = new File(groundTruthFolder, f.getName());
				if (f.getName().endsWith(EXTENSION) && !fIngroundTruthFolder.exists()) {
					// it does not exist in actual folder
					retrievedInexistentFeature_counter++;
					String name = f.getName().substring(0, f.getName().length() - EXTENSION.length());
					inexistent.append(name);
					inexistent.append(",");
				}
			}
		}
		resultsContent.append(retrievedInexistentFeature_counter + ",");
		if (retrievedInexistentFeature_counter > 0) {
			// remove last comma
			inexistent.setLength(inexistent.length() - 1);
			// append list
			resultsContent.append(inexistent.toString());
		}

		return resultsContent.toString();
	}

}
