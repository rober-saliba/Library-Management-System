package control;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;

import boundary.Client;
import entity.ConstantsAndGlobalVars;
import entity.IClient;
import entity.MsgParser;
import entity.User;
import enums.UserStatus;
/**
 * This class controls the functionality of the {@link entity.Librarian} entity.
 * {@code implements} {@link entity.IClient} as it needs to receive responses from the server.
 * Uses the design pattern Singleton to assure that <b>ONLY ONE INSTANCE</b> is created for the lifetime of the program.
 */
public class LibrarianController implements IClient{
	/**
	 * a {@link boundary.Client} instance to send messages to the server.
	 */
	private Client client;
	/**
	 * the single instance of this class
	 */
	private static LibrarianController singleton = null;
	/**
	 * a semaphore that blocks the main thread until a response is received from the server.
	 * semaphore is acquired each time a request to the server is sent and released after the response arrives.
	 */
	private Semaphore sem;
	/**
	 * stores a return results from the server.
	 */
	private ArrayList<String> ReturnBookResult;
	/**
	 * stores a return results from the server.
	 */
	private boolean retVal;
	/**
	 * stores a return results from the server.
	 */
	private User resultUser = null;

	/**
	 * a private constructor that connects the client to the server and initializes some instance variables
	 * including the semaphore which is initialized to 0.
	 * @param host - the IP address of the server.
	 * @param port - the port which the server dwells on.
	 */

	private LibrarianController(String host,int port) {
		try {
			client = new Client(host, port,this);
			sem = new Semaphore(0);
			ReturnBookResult= new ArrayList<>();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	/**
	 * returns the instance if it exists, creates one and returns it if it doesn't exist.
	 * marked as {@code synchronized} to ensure thread safety.
	 * @param host - the IP address of the server.
	 * @param port - the port which the server dwells on.
	 * @return the instance of LibrarianController
	 */
	public synchronized static LibrarianController getInstance(String host,int port) {
		if(singleton == null)
			singleton = new LibrarianController(host,port);
		return singleton;
	}

	/**
	 * disconnects the client from the server.
	 */
	public void disconnectClient() {
		 this.client.quit();
	}
	/**
	 * sends a request to the server to return a book.
	 * @param barcode - the barcode of the copy of the book to return.
	 * @return A String containing the return result message (Success, failure w/ failure reason). 
	 * @throws InterruptedException - thrown should acquiring the semaphore encounter any problems.
	 */

	public ArrayList<String> updateReturnBook(String barcode ) throws InterruptedException

	{
		MsgParser<String> msg = new MsgParser<>();
		msg.addToCommPipe(barcode);
		msg.setTask(ConstantsAndGlobalVars.returnBookTask);
		client.sendMessageToServer(msg);
		// add semaphore and return actual result
		sem.acquire();
		
		return ReturnBookResult;
	}
	
	

	/**
	 * sends a request to the server to add a new user.
	 * @param newUser - the new user to add.
	 * @return true if the addition succeeded, false otherwise.
	 * @throws InterruptedException - thrown should acquiring the semaphore encounter any problems.
	 */
	public boolean addNewUser(User newUser) throws InterruptedException {
		MsgParser<User> msg = new MsgParser<>();
		
		msg.setTask(ConstantsAndGlobalVars.addNewUserTask);
		msg.addToCommPipe(newUser);
		client.sendMessageToServer(msg);
		sem.acquire();
		
		return retVal;
	}
	/**
	 * sends a request to the server to search for a member.
	 * @param userID - the ID of the user to search for.
	 * @return the result user, null if not found.
	 * @throws InterruptedException - thrown should acquiring the semaphore encounter any problems.
	 */
	public User searchForMember(String userID) throws InterruptedException {
		MsgParser<User> msg = new MsgParser<>();
		User user = new User(userID);
		
		msg.setTask(ConstantsAndGlobalVars.getUserTask);
		msg.addToCommPipe(user);
		
		client.sendMessageToServer(msg);
		sem.acquire();
		
		return resultUser;
		
	}

	/**
	 * sends a request to the server to get user status
	 * @param userID - the ID of the user.
	 * @return an object containing the status of the user.
	 * @throws InterruptedException - thrown should acquiring the semaphore encounter any problems.
	 */
	public UserStatus getUserStatus(String userID) throws InterruptedException {
	    MsgParser<String> msg = new MsgParser<>();
	    msg.setTask(ConstantsAndGlobalVars.getUserStatusTask); // Task updated to fetch user status
	    msg.addToCommPipe(userID);

	    client.sendMessageToServer(msg);
	    sem.acquire();

	    return UserStatus.valueOf((String) msg.getCommPipe().get(0)); // Parse status from response
	}

	/**
	 * sends a request to the server to change the status of a user.
	 * @param userID - the ID of the user
	 * @param newStatus - the new status
	 * @return true if succeeded, false otherwise.
	 * @throws InterruptedException - thrown should acquiring the semaphore encounter any problems.
	 */
	public boolean changeMemberStatus(String userID, String newStatus) throws InterruptedException {
		MsgParser<User> msg = new MsgParser<>();
		User u = new User(userID);
		u.setStatus(enums.UserStatus.valueOf(newStatus));
		msg.setTask(ConstantsAndGlobalVars.changeMemberStatusTask);
		msg.addToCommPipe(u);
		
		client.sendMessageToServer(msg);
		sem.acquire();
		
		return retVal;
	}




	
	//handle messages from server
	/**
	 * receives a message from the server and behaves differently depending on the task,
	 * each task requires a different return type and/or value.
	 * at the end it releases the semaphore. 
	 */
	@Override
	public void recieveMessageFromServer(MsgParser msg) {
		// TODO Auto-generated method stub
		if(msg.getTask().equals(ConstantsAndGlobalVars.returnBookTask)) {
			ReturnBookResult= (ArrayList<String>)(msg.getCommPipe());
		}
		if(msg.getTask().equals(ConstantsAndGlobalVars.addNewUserTask)) {
			retVal = ((boolean)msg.getCommPipe().get(0));
		}
		
		if(msg.getTask().equals(ConstantsAndGlobalVars.getUserTask)) {
			if(!(msg.getCommPipe().isEmpty())) {
				resultUser = (User)(msg.getCommPipe().get(0));
				}
		}
		

		
		
		
		sem.release();
	}
}
