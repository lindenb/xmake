package com.github.lindenb.xmake;


public class EvalException extends Exception
	{

	public EvalException() {
		super();
	}

	public EvalException(String message, Throwable cause,
			boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	public EvalException(String message, Throwable cause) {
		super(message, cause);
	}

	public EvalException(String message) {
		super(message);
	}

	public EvalException(Throwable cause) {
		super(cause);
	}
	
	}
