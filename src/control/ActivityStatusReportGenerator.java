package control;

import java.io.File;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.data.general.DefaultPieDataset;

public class ActivityStatusReportGenerator {

    // Generate chart using JFreeChart
    public File createChart() {
        try {
            // Fetch user activity data
            int[] userCounts = DBController.getUserCountsByStatus();

            // Create a dataset with counts included in the labels
            DefaultPieDataset dataset = new DefaultPieDataset();
            dataset.setValue("Active (" + userCounts[0] + ")", userCounts[0]);
            dataset.setValue("Frozen (" + userCounts[1] + ")", userCounts[1]);

            // Create a Pie Chart
            JFreeChart pieChart = ChartFactory.createPieChart(
                "User Activity Status", // Chart title
                dataset,               // Data
                true,                  // Include legend
                true,                  // Include tooltips
                false                  // Exclude URLs
            );

            // Save the chart as an image
            File chartFile = new File("ActivityStatusReport.png");
            ChartUtils.saveChartAsPNG(chartFile, pieChart, 800, 600);

            return chartFile;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
