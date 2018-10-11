package withmi_1.edu.networkcusp.jackson.simple.reader;

import plv.colorado.edu.quantmchecker.qual.Bound;
import plv.colorado.edu.quantmchecker.qual.Inv;

/**
 * ParseException explains why and where the error occurs in source JSON text.
 * 
 * @author FangYidong<fangyidong@yahoo.com.cn>
 *
 */
public class ParseFailure extends Exception {
	private static final long serialVersionUID = -7880698968187728548L;
	
	public static final int ERROR_UNEXPECTED_CHAR = 0;
	public static final int ERROR_UNEXPECTED_TOKEN = 1;
	public static final int ERROR_UNEXPECTED_EXCEPTION = 2;

	private int errorType;
	private Object unexpectedObject;
	private int position;
	
	public ParseFailure(int errorType){
		this(-1, errorType, null);
	}
	
	public ParseFailure(int errorType, Object unexpectedObject){
		this(-1, errorType, unexpectedObject);
	}
	
	public ParseFailure(int position, int errorType, Object unexpectedObject){
		this.position = position;
		this.errorType = errorType;
		this.unexpectedObject = unexpectedObject;
	}
	
	public int pullErrorType() {
		return errorType;
	}
	
	public void fixErrorType(int errorType) {
		this.errorType = errorType;
	}
	
	/**
	 * @see JACKSONParser#pullPosition()
	 * 
	 * @return The character position (starting with 0) of the input where the error occurs.
	 */
	public int getPosition() {
		return position;
	}
	
	public void definePosition(int position) {
		this.position = position;
	}
	
	/**
	 * @see Yytoken
	 * 
	 * @return One of the following base on the value of errorType:
	 * 		   	ERROR_UNEXPECTED_CHAR		java.lang.Character
	 * 			ERROR_UNEXPECTED_TOKEN		org.json.simple.parser.Yytoken
	 * 			ERROR_UNEXPECTED_EXCEPTION	java.lang.Exception
	 */
	public Object takeUnexpectedObject() {
		return unexpectedObject;
	}
	
	public void defineUnexpectedObject(Object unexpectedObject) {
		this.unexpectedObject = unexpectedObject;
	}
	
	public String toString(){
		@Inv("= sb (+ c79 c80 c81 c82 c83 c86 c87 c88 c89 c90 c93 c94 c95 c96 c99 c100 c101)") StringBuffer sb = new StringBuffer();
		@Bound("5") int i;
		switch(errorType){
		case ERROR_UNEXPECTED_CHAR:
			c79: sb.append("Unexpected character (");
			c80: sb.append(unexpectedObject);
			c81: sb.append(") at position ");
			c82: sb.append(position);
			c83: sb.append(".");
			break;
		case ERROR_UNEXPECTED_TOKEN:
			c86: sb.append("Unexpected token ");
			c87: sb.append(unexpectedObject);
			c88: sb.append(" at position ");
			c89: sb.append(position);
			c90: sb.append(".");
			break;
		case ERROR_UNEXPECTED_EXCEPTION:
			c93: sb.append("Unexpected exception at position ");
			c94: sb.append(position);
			c95: sb.append(": ");
			c96: sb.append(unexpectedObject);
			break;
		default:
			c99: sb.append("Unkown error at position ");
			c100: sb.append(position);
			c101: sb.append(".");
			break;
		}
		return sb.toString();
	}
}
