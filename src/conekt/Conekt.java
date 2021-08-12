package conekt;


import com.zerodhatech.kiteconnect.KiteConnect;
//import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
//import com.zerodhatech.kiteconnect.utils.Constants;

import okhttp3.*;
import com.google.gson.*;

import java.util.*;
import java.io.*;


public class Conekt extends KiteConnect 
{
	
	public Conekt(String apiKey) 
	{
		super("");
	}
	
	private final static String LOGIN_URL = "https://kite.zerodha.com/api/login";
	private final static String TWOFA_URL = "https://kite.zerodha.com/api/twofa";

	private static String user_id ; 
	private static String password ;
	private static String twofa_val;
	
	//func to set user_id, password, twofa_val
	void setCred(String id,String pass,String twofa)
	{
		user_id = id;
		password = pass;
		twofa_val = twofa;
	}
	
	public static void main(String[] args) 
	{
		//cookieJar for persistent session
		CookieJar MyCookieJar = new CookieJar() {
	        private final HashMap<String, List<Cookie>> cookieStore = new HashMap<>();

	    
	        public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
	            cookieStore.put(url.host(), cookies);
	        }

	        
	        public List<Cookie> loadForRequest(HttpUrl url) {
	            List<Cookie> cookies = cookieStore.get(url.host());
	            return cookies != null ? cookies : new ArrayList<Cookie>();
	        }
	    };
	    
	    //make client
	    OkHttpClient client = new OkHttpClient.Builder()
	    		.cookieJar(MyCookieJar)
	    		.build();
	    
	    //Call for login
	    try {
			login(client);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			System.out.println("Cannot make requests. Please check your internet connection.");
		}
	    
	}
	
	//Lets user login like in similar manner as using a browser
	static void login(OkHttpClient client) throws IOException
	{
		
		//body for request to POST creds
		RequestBody cred = new FormBody.Builder()
				.add("user_id",user_id)
				.add("password",password)
				.build();
		
		//POST user_id and password
		Request postCredentials = new Request.Builder()
				.url(LOGIN_URL)
				.addHeader("user-agent","Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.164 Safari/537.36")
				.addHeader("accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/* ;q=0.8,application/signed-exchange;v=b3;q=0.9")
				.addHeader("accept-language", "en-GB,en-US;q=0.9,en;q=0.8")
				.post(cred)
				.build();
		
		//store request_id for posting twofa later
		String request_id = null;
		String message = null;
		
		try(Response response = client.newCall(postCredentials).execute())
		{
			JsonObject j = new Gson().fromJson(response.body().string(), JsonObject.class);
			
			request_id = j.get("data").getAsJsonObject().get("request_id").getAsString();
			if(j.has("message"))
			{
				message = j.get("message").getAsString();
			}
		}
		catch (IOException e)
		{
			System.out.println("message: " + message);
		}
		
		//body for request to post twofa
		RequestBody twofa = new FormBody.Builder()
				.add("user_id", user_id)
				.add("request_id", request_id)
				.add("twofa_value", twofa_val)
				.build();
		
		//POST twofa
		Request postTwofa = new Request.Builder()
				.url(TWOFA_URL)
				.addHeader("user-agent","Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.164 Safari/537.36")
				.addHeader("accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/* ;q=0.8,application/signed-exchange;v=b3;q=0.9")
				.addHeader("accept-language", "en-GB,en-US;q=0.9,en;q=0.8")
				.post(twofa)
				.build();
		
		try(Response response = client.newCall(postTwofa).execute())
		{
			JsonObject j = new Gson().fromJson(response.body().string(), JsonObject.class);
			if(j.has("message"))
			{
				message = j.get("message").getAsString();
			}
		}
		catch(IOException e)
		{
			System.out.println("message: " + message);
		}
	
	}
}
