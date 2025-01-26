package control;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.category.DefaultCategoryDataset;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class BorrowingPeriodChartGenerator {

	public File generateBorrowingPeriodChart(Map<String, Map<String, Integer>> borrowingData, String outputFilePath) {
	    // Create the dataset for the chart
	    CategoryDataset dataset = createDataset(borrowingData);

	    // Create the bar chart
	    JFreeChart barChart = ChartFactory.createBarChart(
	        "Borrowing Period Analysis",
	        "Borrowing Categories",
	        "Number of Books",
	        dataset
	    );

	    // Save the chart as a PNG file and return the File
	    File outputFile = new File(outputFilePath);
	    try {
	        ChartUtils.saveChartAsPNG(outputFile, barChart, 800, 600);
	        System.out.println("Chart saved successfully at: " + outputFilePath);
	    } catch (IOException e) {
	        e.printStackTrace();
	    }
	    return outputFile;
	}


    private CategoryDataset createDataset(Map<String, Map<String, Integer>> borrowingData) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();

        // Add On-Time Returns data
        Map<String, Integer> onTimeReturns = borrowingData.get("onTimeReturns");
        for (Map.Entry<String, Integer> entry : onTimeReturns.entrySet()) {
            dataset.addValue(entry.getValue(), "On-Time Returns", entry.getKey());
        }

        // Add Late Returns data
        Map<String, Integer> lateReturns = borrowingData.get("lateReturns");
        for (Map.Entry<String, Integer> entry : lateReturns.entrySet()) {
            dataset.addValue(entry.getValue(), "Late Returns", entry.getKey());
        }

        return dataset;
    }
}
