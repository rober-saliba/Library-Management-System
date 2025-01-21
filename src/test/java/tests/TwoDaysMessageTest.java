package tests;

import entity.TwoDaysMessage;
import control.EmailController;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import org.mockito.Mockito.*;


import java.util.Timer;
import java.util.TimerTask;

class TwoDaysMessageTest {

    @Test
    void testTwoDaysMessageTriggersEmailImmediately() {
        // Mock the EmailController
        EmailController emailController = mock(EmailController.class);

        // Create a mock Timer
        Timer mockTimer = mock(Timer.class);

        // Create an instance of TwoDaysMessage and inject the mock Timer
        TwoDaysMessage message = new TwoDaysMessage();
        message.setTimer(mockTimer);
        message.setRealBarcode("12345");

        // Simulate Timer behavior to run tasks immediately
        doAnswer(invocation -> {
            TimerTask task = invocation.getArgument(0);
            task.run(); // Trigger the task immediately
            return null;
        }).when(mockTimer).schedule(any(TimerTask.class), anyLong());

        // Schedule the timer to send an email
        mockTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                emailController.sendEmail(
                    "waelswaid@gmail.com",
                    "Book Available",
                    "Your reserved book with barcode 12345 is available."
                );
            }
        }, 0); // Immediate execution for testing

        // Verify that the email was sent
        verify(emailController, times(1)).sendEmail(
            "test@example.com",
            "Book Available",
            "Your reserved book with barcode 12345 is available."
        );
    }
}
