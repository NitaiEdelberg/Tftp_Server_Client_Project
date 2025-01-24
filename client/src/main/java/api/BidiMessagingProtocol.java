package api;

public interface BidiMessagingProtocol<T>  {
	void process(T message);
	
	/**
     * @return true if the connection should be terminated
     */
    boolean shouldTerminate();
	/**
	 * Terminate the client
	 */
	void terminate();
}
