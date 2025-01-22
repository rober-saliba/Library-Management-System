package control;

import java.io.File;
import java.util.List;

import org.apache.commons.mail.EmailAttachment;
import org.apache.commons.mail.MultiPartEmail;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.data.general.DefaultPieDataset;
public class ActivityStatusReportGenerator {

    // Main method to generate the report
	public void generateReport() {
	    try {
	        // Fetch data
	        int[] userCounts = fetchData();

	        // Generate chart
	        File chartFile = createChart(userCounts);

	        // Fetch librarian emails
	        List<String> librarianEmails = DBController.getLibrarianEmails();

	        // Send report by email
	        if (chartFile != null && chartFile.exists() && !librarianEmails.isEmpty()) {
	            sendReportByEmail(chartFile, librarianEmails);
	        } else {
	            System.out.println("Failed to generate chart or no librarians found. Email not sent.");
	        }

	    } catch (Exception e) {
	        e.printStackTrace();
	    }
	}


    // Fetch user activity data from the database
    public int[] fetchData() {
        int[] userCounts = new int[2]; // [active, frozen]
        try {
            // Using DBController as per project convention
            userCounts = DBController.getUserCountsByStatus();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return userCounts;
    }

    // Generate chart using JFreeChart
public File createChart(int[] userCounts) {
    try {
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

    // Send the generated chart by email
private void sendReportByEmail(File chartFile, List<String> emailList) {
    try {
        for (String email : emailList) {
            // Prepare the email subject and body
            String subject = "Monthly Activity Status Report";
            String body = "Please find the attached Activity Status Report for this month.";

            // Use ReportEmailController to send the email with the attachment
            ReportEmailController.sendReportEmail(email, subject, body, chartFile);

            System.out.println("Email sent successfully to: " + email);
        }
    } catch (Exception e) {
        e.printStackTrace();
    }
}

}
