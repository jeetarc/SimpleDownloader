package com.jeet.simpledownloader;

/*
 * Copyright (c) 2026 Jeet Jati, under jeetarc.
 *
 * This source code is part of SimpleDownloader.
 */
 
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class DownloadException extends IOException {
	
	public enum Type {
		NETWORK_LOST, TIMEOUT, DNS_ERROR, SSL_ERROR,
		HTTP_ERROR, ENOSPC, FILE_ERROR, STORAGE_PERMISSION_DENIED, 
		OUTPUT_URI_INVALID, RANGE_NOT_SUPPORTED, EMPTY_RESPONSE, 
		CANCELLED, UNKNOWN
	}
	
	private final Type type;
	private final int code;
	private final boolean retryable;
	
	public DownloadException(Type type, String message) {
		this(type, message, -1, false);
	}
	
	public DownloadException(Type type, String message, Throwable cause) {
		this(type, message, -1, false, cause);
	}
	
	public DownloadException(Type type, String message, int code, boolean retryable) {
		super(message);
		this.type = type == null ? Type.UNKNOWN : type;
		this.code = code;
		this.retryable = retryable;
	}
	
	public DownloadException(Type type, String message, int code, boolean retryable, Throwable cause) {
		super(message, cause);
		this.type = type == null ? Type.UNKNOWN : type;
		this.code = code;
		this.retryable = retryable;
	}
	
	public Type getType() {
		return type;
	}
	
	public int getCode() {
		return code;
	}
	
	public boolean isRetryable() {
		return retryable;
	}
	
	@Override
	public String toString() {
		String message = getMessage();
		String displayType = type != null ? type.name() : Type.UNKNOWN.name();
		
		if (message == null || message.length() == 0) {
			return getClass().getName() + ": (" + displayType + ")";
		}
		
		return getClass().getName() + ": (" + displayType + ") " + message;
	}
	
	public static DownloadException http(int code) {
		return new DownloadException(
		Type.HTTP_ERROR,
		HttpStatus.getFullMessage(code),
		code,
		HttpStatus.isRetryable(code)
		);
	}
	
	public static DownloadException http(int code, Throwable cause) {
		return new DownloadException(
		Type.HTTP_ERROR,
		HttpStatus.getFullMessage(code),
		code,
		HttpStatus.isRetryable(code),
		cause
		);
	}
	
	public static DownloadException networkLost(Throwable cause) {
		return new DownloadException(
		Type.NETWORK_LOST,
		"Network connection lost.",
		-1,
		true,
		cause
		);
	}
	
	public static DownloadException timeout(Throwable cause) {
		return new DownloadException(
		Type.TIMEOUT,
		"Connection timed out.",
		-1,
		true,
		cause
		);
	}
	
	public static DownloadException dnsError(Throwable cause) {
		return new DownloadException(
		Type.DNS_ERROR,
		"DNS lookup failed.",
		-1,
		true,
		cause
		);
	}
	
	public static DownloadException sslError(Throwable cause) {
		return new DownloadException(
		Type.SSL_ERROR,
		"SSL error.",
		-1,
		true,
		cause
		);
	}
	
	public static DownloadException enospc(Throwable cause) {
		return new DownloadException(
		Type.ENOSPC,
		"No space left on device.",
		-1,
		false,
		cause
		);
	}
	
	public static DownloadException fileError(String message, Throwable cause) {
		return new DownloadException(
		Type.FILE_ERROR,
		message == null || message.length() == 0 ? "File error." : message,
		-1,
		false,
		cause
		);
	}
	
	public static DownloadException emptyResponse(String message) {
		return new DownloadException(
		Type.EMPTY_RESPONSE,
		message == null || message.length() == 0 ? "Empty response." : message,
		-1,
		true
		);
	}
	
	static final class HttpStatus {
		private static final Map<Integer, String> FULL = new HashMap<>();
		
		static {
			FULL.put(100, "100 Continue: The server received the initial request headers and is waiting for the remaining request body.");
			FULL.put(101, "101 Switching Protocols: The server is changing to a different protocol as requested by the client.");
			FULL.put(102, "102 Processing: The server has accepted the request and is still processing it.");
			FULL.put(103, "103 Early Hints: The server sends preliminary headers before the final response.");
			FULL.put(200, "200 OK: The request completed successfully.");
			FULL.put(201, "201 Created: A new resource was successfully created on the server.");
			FULL.put(202, "202 Accepted: The request was accepted but processing has not finished yet.");
			FULL.put(203, "203 Non-Authoritative Information: The returned data was modified by a proxy or intermediary.");
			FULL.put(204, "204 No Content: The request succeeded but no content was returned.");
			FULL.put(205, "205 Reset Content: The client should reset the current view or form.");
			FULL.put(206, "206 Partial Content: The server returned only part of the requested data.");
			FULL.put(207, "207 Multi-Status: Multiple status values are returned for different operations.");
			FULL.put(208, "208 Already Reported: The resource has already been listed in a previous response.");
			FULL.put(226, "226 IM Used: The server fulfilled the request using instance manipulation.");
			FULL.put(300, "300 Multiple Choices: Multiple possible resources are available for the request.");
			FULL.put(301, "301 Moved Permanently: The requested resource has permanently moved to another URL.");
			FULL.put(302, "302 Found: The resource temporarily exists at a different URL.");
			FULL.put(303, "303 See Other: The response should be retrieved from another URL using GET.");
			FULL.put(304, "304 Not Modified: The resource has not changed since the last request.");
			FULL.put(305, "305 Use Proxy: Access to the resource requires a proxy server.");
			FULL.put(306, "306 Switch Proxy: This status code is no longer used.");
			FULL.put(307, "307 Temporary Redirect: The request should be repeated at another temporary URL.");
			FULL.put(308, "308 Permanent Redirect: The request should use a new permanent URL.");
			FULL.put(400, "400 Bad Request: The server cannot process the request because it contains invalid syntax.");
			FULL.put(401, "401 Unauthorized: Authentication is required to access this resource.");
			FULL.put(402, "402 Payment Required: Access requires payment.");
			FULL.put(403, "403 Forbidden: The server denied access to the requested resource.");
			FULL.put(404, "404 Not Found: The requested page or resource could not be found.");
			FULL.put(405, "405 Method Not Allowed: The HTTP method used is not allowed for this resource.");
			FULL.put(406, "406 Not Acceptable: The requested response format is unavailable.");
			FULL.put(407, "407 Proxy Authentication Required: Authentication with a proxy server is required.");
			FULL.put(408, "408 Request Timeout: The server timed out waiting for the request.");
			FULL.put(409, "409 Conflict: The request conflicts with the current state of the resource.");
			FULL.put(410, "410 Gone: The requested resource has been permanently removed.");
			FULL.put(411, "411 Length Required: The server requires a Content-Length header.");
			FULL.put(412, "412 Precondition Failed: One or more request conditions were not met.");
			FULL.put(413, "413 Payload Too Large: The request body exceeds the server limit.");
			FULL.put(414, "414 URI Too Long: The requested URL is too long.");
			FULL.put(415, "415 Unsupported Media Type: The request format is not supported.");
			FULL.put(416, "416 Range Not Satisfiable: The requested file range cannot be provided.");
			FULL.put(417, "417 Expectation Failed: The server could not satisfy the Expect request header.");
			FULL.put(418, "418 I'm a Teapot: The server refuses because it is a teapot.");
			FULL.put(421, "421 Misdirected Request: The request was sent to the wrong server.");
			FULL.put(422, "422 Unprocessable Content: The server cannot process the request.");
			FULL.put(423, "423 Locked: The requested resource is locked.");
			FULL.put(424, "424 Failed Dependency: The request failed because another required request failed.");
			FULL.put(425, "425 Too Early: The server is unwilling to process the request yet.");
			FULL.put(426, "426 Upgrade Required: The client must upgrade to a different protocol.");
			FULL.put(428, "428 Precondition Required: The server requires conditional requests.");
			FULL.put(429, "429 Too Many Requests: Too many requests were sent in a short period.");
			FULL.put(431, "431 Request Header Fields Too Large: Request headers exceed the server limit.");
			FULL.put(451, "451 Unavailable For Legal Reasons: The resource is unavailable for legal reasons.");
			FULL.put(500, "500 Internal Server Error: The server encountered an unexpected error.");
			FULL.put(501, "501 Not Implemented: The server does not support the requested functionality.");
			FULL.put(502, "502 Bad Gateway: The server received an invalid response from another server.");
			FULL.put(503, "503 Service Unavailable: The server is temporarily unavailable or overloaded.");
			FULL.put(504, "504 Gateway Timeout: The server timed out while waiting for another server.");
			FULL.put(505, "505 HTTP Version Not Supported: The HTTP version is not supported by the server.");
			FULL.put(506, "506 Variant Also Negotiates: The server configuration caused a negotiation loop.");
			FULL.put(507, "507 Insufficient Storage: The server does not have enough storage space.");
			FULL.put(508, "508 Loop Detected: The server detected an infinite processing loop.");
			FULL.put(510, "510 Not Extended: Additional request extensions are required.");
			FULL.put(511, "511 Network Authentication Required: Network authentication is required before access.");
		}
		
		private HttpStatus() {}
		
		static String getFullMessage(int code) {
			String message = FULL.get(code);
			if (message != null) return message;
			if (code >= 100 && code < 200) return code + " Informational Response.";
			if (code >= 200 && code < 300) return code + " Success Response.";
			if (code >= 300 && code < 400) return code + " Redirection Response.";
			if (code >= 400 && code < 500) return code + " Client Error.";
			if (code >= 500 && code < 600) return code + " Server Error.";
			return code + " Unknown HTTP status.";
		}
		
		static boolean isRetryable(int code) {
			return code == 408 || code == 425 || code == 429 || code == 500 || code == 502 || code == 503 || code == 504 || code == 507;
		}
	}
}

final class RefreshRequestException extends Exception {
	final long downloadedTotal;
	
	RefreshRequestException(long downloadedTotal) {
		this.downloadedTotal = downloadedTotal;
	}
}
