package com.conekt;

import com.conekt.conektHTTP.conektRequestHandler;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import com.zerodhatech.kiteconnect.Routes;
import com.zerodhatech.kiteconnect.kitehttp.SessionExpiryHook;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.kiteconnect.utils.Constants;
import com.zerodhatech.models.*;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.supercsv.cellprocessor.*;
import org.supercsv.cellprocessor.constraint.NotNull;
import org.supercsv.cellprocessor.ift.CellProcessor;
import org.supercsv.io.CsvBeanReader;
import org.supercsv.io.ICsvBeanReader;
import org.supercsv.prefs.CsvPreference;

import java.io.*;
import java.lang.reflect.Type;
import java.net.Proxy;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Conekt was built from the official Java client for KiteConnect API
 * Be sure to import com.zerodhatech.* along with com.conekt.* to make use of Models and Constants from the official Client
 * 
 * Follow the official documentation for Java client to use Conekt.
 */
public class Conekt {

	private final static String LOGIN_URL = "https://kite.zerodha.com/api/login";
	private final static String TWOFA_URL = "https://kite.zerodha.com/api/twofa";

	private static String userId ; 
	private static String password ;
	private static String twofa_val;
	private static String enctoken;
	private static CookieJar MyCookieJar;
	private static conektRequestHandler RequestHandler;
	
    public static SessionExpiryHook sessionExpiryHook = null;
    public static boolean ENABLE_LOGGING = false;
	private Proxy proxy = null;
    private Routes routes = new Routes();
    private Gson gson;
    
    
    /**
     * Intializes Conekt with no proxy and logging
     */
    public Conekt()
    {
    	this(null, false);
    }
    
    /** Initializes Conekt
     * @param userProxy is the user defined proxy. Can be used only if a user chose to use the proxy.
     * @param enabledebugLog is a boolean to enable logs
     */
    public Conekt(Proxy userProxy, boolean enableDebugLog) {
        this.proxy = userProxy;
        
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(Date.class, new JsonDeserializer<Date>() {

            public Date deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
                try {
                    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    return format.parse(jsonElement.getAsString());
                } catch (ParseException e) {
                    return null;
                }
            }
        });
        
        gson = gsonBuilder.setDateFormat("yyyy-MM-dd HH:mm:ss").create();
        ENABLE_LOGGING = enableDebugLog;
    }
    
    
    /** Starts a login Session
     * @param userId is your Kite userId
     * @param password is your Kite password
     * @param twofa_val is your Two Factor Authentication PIN for Kite
     */
	public void setCred(String userId,String password,String twofa_val)
  	{
  		Conekt.userId = userId;
  		Conekt.password = password;
  		Conekt.twofa_val = twofa_val;
  		
  			//cookieJar for persistent session
  			MyCookieJar = new CookieJar() {
  		        private final HashMap<String, List<Cookie>> cookieStore = new HashMap<>();

  		        public void saveFromResponse(HttpUrl url, List<Cookie> cookies) 
  		        {
  		            cookieStore.put(url.host(), cookies);
  		            for(Cookie cookie:cookies)
  		            {
  		            	if("enctoken".equals(cookie.value()))
  		            	{
  		            		Conekt.enctoken = cookie.value();
  		            	}
  		            }
  		        }
  		        
  		        public List<Cookie> loadForRequest(HttpUrl url) 
  		        {
  		            List<Cookie> cookies = cookieStore.get(url.host());
  		            return cookies != null ? cookies : new ArrayList<Cookie>();
  		        }
  		    };
  		    
  		    // Make client with cookieJar
  		    OkHttpClient client = new OkHttpClient.Builder()
  		    		.cookieJar(MyCookieJar)
  		    		.build();
  		    
  		    // Call for login
  		    try 
  		    {
  		    	login(client);
  			} 
  		    catch (IOException e) 
  		    {
  				e.printStackTrace();
  		    }
  		    
  		    // Initiate RequestHandler 
  		    RequestHandler = new conektRequestHandler(userId,proxy);
  	}

	/**
	 * Simulates Login from browser
	 * @param client is the HTTP Client used to make requests
	 * @throws IOException is thrown when there is connection error
	 */
  	static String login(OkHttpClient client) throws IOException
  	{
  		
  		//body for request to POST creds
  		RequestBody cred = new FormBody.Builder()
  				.add("user_id",userId)
  				.add("password",password)
  				.build();
  		
  		//POST userId and password
  		Request postCredentials = new Request.Builder()
  				.url(LOGIN_URL)
  				.addHeader("user-agent","Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.164 Safari/537.36")
  				.addHeader("accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/* ;q=0.8,application/signed-exchange;v=b3;q=0.9")
  				.addHeader("accept-language", "en-GB,en-US;q=0.9,en;q=0.8")
  				.post(cred)
  				.build();
  		
  		//store request_id for posting twofa_val later
  		String request_id = null;
  		String message = "{message: Unknown Error}";
  		
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
  			return "{message: " + message + "}";
  		}
  		
  		//body for request to post twofa_val
  		RequestBody twofa = new FormBody.Builder()
  				.add("user_id", userId)
  				.add("request_id", request_id)
  				.add("twofa_value", twofa_val)
  				.build();
  		
  		//POST twofa_val
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
  				message = "{message: " + j.get("message").getAsString() + "}";
  			}
  		}
  		catch(IOException e)
  		{
  			return message;
  		}
		return null;

  	}
    /** Registers callback for session error.
     * @param hook can be set to get callback when session is expired.
     **/
    public void setSessionExpiryHook(SessionExpiryHook hook){
        sessionExpiryHook = hook;
    }

    
    /** Get the profile details of the use.
     * @return Profile is a POJO which contains profile related data.
     * @throws IOException is thrown when there is connection error.
     * @throws KiteException is thrown for all Kite trade related errors.
     * */
    public Profile getProfile() throws IOException, KiteException, JSONException {
        String url = routes.get("user.profile");
        JSONObject response = RequestHandler.getRequest(url, enctoken);
        return gson.fromJson(String.valueOf(response.get("data")), Profile.class);
    }

    /**
     * Gets account balance and cash margin details for a particular segment.
     * Example for segment can be equity or commodity.
     * @param segment can be equity or commodity.
     * @return Margins object.
     * @throws KiteException is thrown for all Kite trade related errors.
     * @throws JSONException is thrown when there is exception while parsing response.
     * @throws IOException is thrown when there is connection error.
     */
    public Margin getMargins(String segment) throws KiteException, JSONException, IOException {
        String url = routes.get("user.margins.segment").replace(":segment", segment);
        JSONObject response = RequestHandler.getRequest(url, enctoken);
        return gson.fromJson(String.valueOf(response.get("data")), Margin.class);
    }

    /**
     * Gets account balance and cash margin details for a equity and commodity.
     * @return Map of String and Margin is a map of commodity or equity string and funds data.
     * @throws KiteException is thrown for all Kite trade related errors.
     * @throws JSONException is thrown when there is exception while parsing response.
     * @throws IOException is thrown when there is connection error.
     */
    public Map<String, Margin> getMargins() throws KiteException, JSONException, IOException {
        String url = routes.get("user.margins");
        JSONObject response = RequestHandler.getRequest(url, enctoken);
        Type type = new TypeToken<Map<String, Margin>>(){}.getType();
        return gson.fromJson(String.valueOf(response.get("data")), type);
    }

    /** Get margins required data before placing an order.
     * @return MarginCalculationData object, it contains the total, var, exposure, span and other components of the margin required.
     * @throws KiteException is thrown for all Kite trade related errors.
     * @throws JSONException is thrown when there is exception while parsing response.
     * @throws IOException is thrown when there is connection error.
     * */
    public List<MarginCalculationData> getMarginCalculation(List<MarginCalculationParams> params) throws IOException, KiteException, JSONException{
        String url = routes.get("margin.calculation.order");
        JSONArray jsonArray = new JSONArray();
        for(int k =0; k< params.size(); k++) {
            MarginCalculationParams param = params.get(k);
            JSONObject jsonObject = new JSONObject();

            jsonObject.put("tradingsymbol", param.tradingSymbol);
            jsonObject.put("exchange", param.exchange);
            jsonObject.put("transaction_type", param.transactionType);
            jsonObject.put("variety", param.variety);
            jsonObject.put("product", param.product);
            jsonObject.put("order_type", param.orderType);
            jsonObject.put("quantity", param.quantity);
            jsonObject.put("price", param.price);
            jsonObject.put("trigger_price", param.triggerPrice);
            jsonArray.put(jsonObject);
        }
        JSONObject response = RequestHandler.postRequestJSON(url, jsonArray, new HashMap<String, Object>(), enctoken);
        return Arrays.asList(gson.fromJson(String.valueOf(response.get("data")), MarginCalculationData[].class));
    }

    /** Get margins required data for multiple instruments before placing an order,
     * this can be used to check the margin required for taking hedged positions.
     * @return CombinedMarginData object, it contains the initial, final and margin calculation for each order.
     * @throws KiteException is thrown for all Kite trade related errors.
     * @throws JSONException is thrown when there is exception while parsing response.
     * @throws IOException is thrown when there is connection error.
     * */
    public CombinedMarginData getCombinedMarginCalculation(List<MarginCalculationParams> params, boolean considerPositions, boolean compactMode) throws IOException, KiteException, JSONException{
        String url = routes.get("margin.calculation.basket");
        Map<String, Object> queryParams = new HashMap<String, Object>();

        if(considerPositions) queryParams.put("consider_positions", true);
        if(compactMode) queryParams.put("mode", "compact");

        JSONArray jsonArray = new JSONArray();
        for(int k = 0; k < params.size(); k++){
            MarginCalculationParams param = params.get(k);
            JSONObject jsonObject = new JSONObject();

            jsonObject.put("tradingsymbol", param.tradingSymbol);
            jsonObject.put("exchange", param.exchange);
            jsonObject.put("transaction_type", param.transactionType);
            jsonObject.put("variety", param.variety);
            jsonObject.put("product", param.product);
            jsonObject.put("order_type", param.orderType);
            jsonObject.put("quantity", param.quantity);
            jsonObject.put("price", param.price);
            jsonObject.put("trigger_price", param.triggerPrice);
            jsonArray.put(jsonObject);
        }
        JSONObject response = RequestHandler.postRequestJSON(url, jsonArray, queryParams, enctoken);
        return gson.fromJson(String.valueOf(response.get("data")), CombinedMarginData.class);
    }

    /**
     * Places an order.
     * @param orderParams is Order params.
     * @param variety variety="regular". Order variety can be bo, co, amo, regular.
     * @return Order contains only orderId.
     * @throws KiteException is thrown for all Kite trade related errors.
     * @throws JSONException is thrown when there is exception while parsing response.
     * @throws IOException is thrown when there is connection error.
     */
    public Order placeOrder(OrderParams orderParams, String variety) throws KiteException, JSONException, IOException {
        String url = routes.get("orders.place").replace(":variety", variety);

        Map<String, Object> params = new HashMap<>();

        if(orderParams.exchange != null) params.put("exchange", orderParams.exchange);
        if(orderParams.tradingsymbol != null) params.put("tradingsymbol", orderParams.tradingsymbol);
        if(orderParams.transactionType != null) params.put("transaction_type", orderParams.transactionType);
        if(orderParams.quantity != null) params.put("quantity", orderParams.quantity);
        if(orderParams.price != null) params.put("price", orderParams.price);
        if(orderParams.product != null) params.put("product", orderParams.product);
        if(orderParams.orderType != null) params.put("order_type", orderParams.orderType);
        if(orderParams.validity != null) params.put("validity", orderParams.validity);
        if(orderParams.disclosedQuantity != null) params.put("disclosed_quantity", orderParams.disclosedQuantity);
        if(orderParams.triggerPrice != null) params.put("trigger_price", orderParams.triggerPrice);
        if(orderParams.squareoff != null) params.put("squareoff", orderParams.squareoff);
        if(orderParams.stoploss != null) params.put("stoploss", orderParams.stoploss);
        if(orderParams.trailingStoploss != null) params.put("trailing_stoploss", orderParams.trailingStoploss);
        if(orderParams.tag != null) params.put("tag", orderParams.tag);

        JSONObject jsonObject = RequestHandler.postRequest(url, params, enctoken);
        Order order =  new Order();
        order.orderId = jsonObject.getJSONObject("data").getString("order_id");
        return order;
    }

    /**
     * Modifies an open order.
     * @param orderParams is Order params.
     * @param variety variety="regular". Order variety can be bo, co, amo, regular.
     * @param orderId order id of the order being modified.
     * @return Order object contains only orderId.
     * @throws KiteException is thrown for all Kite trade related errors.
     * @throws JSONException is thrown when there is exception while parsing response.
     * @throws IOException is thrown when there is connection error.
     */
    public Order modifyOrder(String orderId, OrderParams orderParams, String variety) throws KiteException, JSONException, IOException {
        String url = routes.get("orders.modify").replace(":variety", variety).replace(":order_id", orderId);

        Map<String, Object> params = new HashMap<>();

        if(orderParams.exchange != null) params.put("exchange", orderParams.exchange);
        if(orderParams.tradingsymbol != null) params.put("tradingsymbol", orderParams.tradingsymbol);
        if(orderParams.transactionType != null) params.put("transaction_type", orderParams.transactionType);
        if(orderParams.quantity != null) params.put("quantity", orderParams.quantity);
        if(orderParams.price != null) params.put("price", orderParams.price);
        if(orderParams.product != null) params.put("product", orderParams.product);
        if(orderParams.orderType != null) params.put("order_type", orderParams.orderType);
        if(orderParams.validity != null) params.put("validity", orderParams.validity);
        if(orderParams.disclosedQuantity != null) params.put("disclosed_quantity", orderParams.disclosedQuantity);
        if(orderParams.triggerPrice != null) params.put("trigger_price", orderParams.triggerPrice);
        if(orderParams.squareoff != null) params.put("squareoff", orderParams.squareoff);
        if(orderParams.stoploss != null) params.put("stoploss", orderParams.stoploss);
        if(orderParams.trailingStoploss != null) params.put("trailing_stoploss", orderParams.trailingStoploss);
        if(orderParams.parentOrderId != null) params.put("parent_order_id", orderParams.parentOrderId);

        JSONObject jsonObject = RequestHandler.putRequest(url, params, enctoken);
        Order order =  new Order();
        order.orderId = jsonObject.getJSONObject("data").getString("order_id");
        return order;
    }

    /**
     * Cancels an order.
     * @param orderId order id of the order to be cancelled.
     * @param variety [variety="regular"]. Order variety can be bo, co, amo, regular.
     * @return Order object contains only orderId.
     * @throws KiteException is thrown for all Kite trade related errors.
     * @throws JSONException is thrown when there is exception while parsing response.
     * @throws IOException is thrown when there is connection error.
     */
    public Order cancelOrder(String orderId, String variety) throws KiteException, JSONException, IOException {
        String url = routes.get("orders.cancel").replace(":variety", variety).replace(":order_id", orderId);
        Map<String, Object> params = new HashMap<String, Object>();

        JSONObject jsonObject = RequestHandler.deleteRequest(url, params, enctoken);
        Order order =  new Order();
        order.orderId = jsonObject.getJSONObject("data").getString("order_id");
        return order;
    }

    /**
     * Cancel/exit special orders like BO, CO
     * @param parentOrderId order id of first leg.
     * @param orderId order id of the order to be cancelled.
     * @param variety [variety="regular"]. Order variety can be bo, co, amo, regular.
     * @return Order object contains only orderId.
     * @throws KiteException is thrown for all Kite trade related errors.
     * @throws IOException is thrown when there is connection error.
     * */
    public Order cancelOrder(String orderId, String parentOrderId, String variety) throws KiteException, IOException, JSONException {
        String url = routes.get("orders.cancel").replace(":variety", variety).replace(":order_id", orderId);

        Map<String, Object> params = new HashMap<>();
        params.put("parent_order_id", parentOrderId);

        JSONObject jsonObject = RequestHandler.deleteRequest(url, params, enctoken);
        Order order =  new Order();
        order.orderId = jsonObject.getJSONObject("data").getString("order_id");
        return order;
    }

    /** Fetches collection of orders from the orderbook.
     * @return List of orders.
     * @throws KiteException is thrown for all Kite trade related errors.
     * @throws JSONException is thrown when there is exception while parsing response.
     * @throws IOException is thrown when there is connection error.
     * */
    public List<Order> getOrders() throws KiteException, JSONException, IOException {
        String url = routes.get("orders");
        JSONObject response = RequestHandler.getRequest(url, enctoken);
        return Arrays.asList(gson.fromJson(String.valueOf(response.get("data")), Order[].class));
    }

    /** Fetches list of gtt existing in an account.
    * @return List of GTTs.
    * @throws KiteException is thrown for all Kite trade related errors.
    * @throws IOException is thrown when there is connection error.
    * */
    public List<GTT> getGTTs() throws KiteException, IOException, JSONException {
        String url = routes.get("gtt");
        JSONObject response = RequestHandler.getRequest(url, enctoken);
        return Arrays.asList(gson.fromJson(String.valueOf(response.get("data")), GTT[].class));
    }

    /** Fetch details of a GTT.
     * @param gttId is the id of the GTT that needs to be fetched.
     * @return GTT object which contains all the details.
     * @throws KiteException is thrown for all Kite trade related errors.
     * @throws IOException is thrown when there is connection error.
     * @throws JSONException is thrown when there is exception while parsing response.
     * */
    public GTT getGTT(int gttId) throws IOException, KiteException, JSONException {
        String url = routes.get("gtt.info").replace(":id", gttId+"");
        JSONObject response = RequestHandler.getRequest(url, enctoken);
        return gson.fromJson(String.valueOf(response.get("data")), GTT.class);
    }

    /** Place a GTT.
     * @param gttParams is GTT param which container condition, type, order details. It can contain one or two orders.
     * @throws IOException  is thrown when there is connection error.
     * @throws KiteException is thrown for all Kite trade related errors.
     * @throws JSONException is thrown when there is exception while parsing response.
     * @return GTT object contains only gttId.*/
    public GTT placeGTT(GTTParams gttParams) throws IOException, KiteException, JSONException {
        String url = routes.get("gtt.place");
        Map<String, Object> params = new HashMap<>();
        Map<String, Object> conditionParam = new HashMap<>();
        JSONArray ordersParam = new JSONArray();

        conditionParam.put("exchange", gttParams.exchange);
        conditionParam.put("tradingsymbol", gttParams.tradingsymbol);
        conditionParam.put("trigger_values", gttParams.triggerPrices.toArray());
        conditionParam.put("last_price", gttParams.lastPrice);
        conditionParam.put("instrument_token", gttParams.instrumentToken);

        for(GTTParams.GTTOrderParams order : gttParams.orders) {
            JSONObject gttOrderItem = new JSONObject();
            gttOrderItem.put("exchange", gttParams.exchange);
            gttOrderItem.put("tradingsymbol", gttParams.tradingsymbol);
            gttOrderItem.put("transaction_type", order.transactionType);
            gttOrderItem.put("quantity", order.quantity);
            gttOrderItem.put("price", order.price);
            gttOrderItem.put("order_type", order.orderType);
            gttOrderItem.put("product", order.product);
            ordersParam.put(gttOrderItem);
        }

        params.put("condition", new JSONObject(conditionParam).toString());
        params.put("orders", ordersParam.toString());
        params.put("type", gttParams.triggerType);

        JSONObject response = RequestHandler.postRequest(url, params, enctoken);
        GTT gtt = new GTT();
        gtt.id = response.getJSONObject("data").getInt("trigger_id");
        return gtt;
    }

    /** Modify a GTT.
     * @param gttParams is GTT param which container condition, type, order details. It can contain one or two orders.
     * @param gttId is the id of the GTT to be modified.
     * @throws IOException  is thrown when there is connection error.
     * @throws KiteException is thrown for all Kite trade related errors.
     * @throws JSONException is thrown when there is exception while parsing response.
     * @return GTT object contains only gttId.*/
    public GTT modifyGTT(int gttId, GTTParams gttParams) throws IOException, KiteException, JSONException {
        String url = routes.get("gtt.modify").replace(":id", gttId+"");
        Map<String, Object> params = new HashMap<>();
        Map<String, Object> conditionParam = new HashMap<>();
        JSONArray ordersParam = new JSONArray();

        conditionParam.put("exchange", gttParams.exchange);
        conditionParam.put("tradingsymbol", gttParams.tradingsymbol);
        conditionParam.put("trigger_values", gttParams.triggerPrices.toArray());
        conditionParam.put("last_price", gttParams.lastPrice);
        conditionParam.put("instrument_token", gttParams.instrumentToken);

        for(GTTParams.GTTOrderParams order : gttParams.orders) {
            JSONObject gttOrderItem = new JSONObject();
            gttOrderItem.put("exchange", gttParams.exchange);
            gttOrderItem.put("tradingsymbol", gttParams.tradingsymbol);
            gttOrderItem.put("transaction_type", order.transactionType);
            gttOrderItem.put("quantity", order.quantity);
            gttOrderItem.put("price", order.price);
            gttOrderItem.put("order_type", order.orderType);
            gttOrderItem.put("product", order.product);
            ordersParam.put(gttOrderItem);
        }

        params.put("condition", new JSONObject(conditionParam).toString());
        params.put("orders", ordersParam.toString());
        params.put("type", gttParams.triggerType);

        JSONObject response = RequestHandler.putRequest(url, params, enctoken);
        GTT gtt = new GTT();
        gtt.id = response.getJSONObject("data").getInt("trigger_id");
        return  gtt;
    }

    /**
     * Cancel GTT.
     * @param gttId order id of first leg.
     * @return GTT object contains only gttId.
     * @throws KiteException is thrown for all Kite trade related errors.
     * @throws IOException is thrown when there is connection error.
     * @throws JSONException is thrown when there is exception while parsing response.
     * */
    public GTT cancelGTT(int gttId) throws IOException, KiteException, JSONException {
        String url = routes.get("gtt.delete").replace(":id", gttId+"");
        JSONObject response  = RequestHandler.deleteRequest(url, new HashMap<>(), enctoken);
        GTT gtt = new GTT();
        gtt.id = response.getJSONObject("data").getInt("trigger_id");
        return gtt;
    }

    /** Returns list of different stages an order has gone through.
     * @return List of multiple stages an order has gone through in the system.
     * @throws KiteException is thrown for all Kite trade related errors.
     * @param orderId is the order id which is obtained from orderbook.
     * @throws KiteException is thrown for all Kite trade related errors.
     * @throws IOException is thrown when there is connection error.
     * */
    public List<Order> getOrderHistory(String orderId) throws KiteException, IOException, JSONException {
        String url = routes.get("order").replace(":order_id", orderId);
        JSONObject response = RequestHandler.getRequest(url, enctoken);
        return Arrays.asList(gson.fromJson(String.valueOf(response.get("data")), Order[].class));
    }

    /**
     * Retrieves list of trades executed.
     * @return List of trades.
     * @throws KiteException is thrown for all Kite trade related errors.
     * @throws JSONException is thrown when there is exception while parsing response.
     * @throws IOException is thrown when there is connection error.
     */
    public List<Trade> getTrades() throws KiteException, JSONException, IOException {
        JSONObject response = RequestHandler.getRequest(routes.get("trades"), enctoken);
        return Arrays.asList(gson.fromJson(String.valueOf(response.get("data")), Trade[].class));
    }

    /**
     * Retrieves list of trades executed of an order.
     * @param orderId order if of the order whose trades are fetched.
     * @return List of trades for the given order.
     * @throws KiteException is thrown for all Kite trade related errors.
     * @throws JSONException is thrown when there is exception while parsing response.
     * @throws IOException is thrown when there is connection error.
     */
    public List<Trade> getOrderTrades(String orderId) throws KiteException, JSONException, IOException {
        JSONObject response = RequestHandler.getRequest(routes.get("orders.trades").replace(":order_id", orderId), enctoken);
        return Arrays.asList(gson.fromJson(String.valueOf(response.get("data")), Trade[].class));
    }

    /**
     * Retrieves the list of holdings.
     * @return List of holdings.
     * @throws KiteException is thrown for all Kite trade related errors.
     * @throws JSONException is thrown when there is exception while parsing response.
     * @throws IOException is thrown when there is connection error.
     */
    public List<Holding> getHoldings() throws KiteException, JSONException, IOException {
        JSONObject response = RequestHandler.getRequest(routes.get("portfolio.holdings"), enctoken);
        return Arrays.asList(gson.fromJson(String.valueOf(response.get("data")), Holding[].class));
    }

    /**
     * Retrieves the list of positions.
     * @return List of positions.
     * @throws KiteException is thrown for all Kite trade related errors.
     * @throws JSONException is thrown when there is exception while parsing response.
     * @throws IOException is thrown when there is connection error.
     */
    public Map<String, List<Position>> getPositions() throws KiteException, JSONException, IOException {
        Map<String, List<Position>> positionsMap = new HashMap<>();
        JSONObject response = RequestHandler.getRequest(routes.get("portfolio.positions"), enctoken);
        JSONObject allPositions = response.getJSONObject("data");
        positionsMap.put("net", Arrays.asList(gson.fromJson(String.valueOf(allPositions.get("net")), Position[].class)));
        positionsMap.put("day", Arrays.asList(gson.fromJson(String.valueOf(allPositions.get("day")), Position[].class)));
        return positionsMap;
    }


    /**
     * Modifies an open position's product type. Only an MIS, CNC, and NRML positions can be converted.
     * @param tradingSymbol Tradingsymbol of the instrument  (ex. RELIANCE, INFY).
     * @param exchange Exchange in which instrument is listed (NSE, BSE, NFO, BFO, CDS, MCX).
     * @param transactionType Transaction type (BUY or SELL).
     * @param positionType day or overnight position
     * @param oldProduct Product code (NRML, MIS, CNC).
     * @param newProduct Product code (NRML, MIS, CNC).
     * @param quantity Order quantity
     * @return JSONObject  which will have status.
     * @throws KiteException is thrown for all Kite trade related errors.
     * @throws JSONException is thrown when there is exception while parsing response.
     * @throws IOException is thrown when there is connection error.
     */
    public JSONObject convertPosition(String tradingSymbol, String exchange, String transactionType, String positionType, String oldProduct, String newProduct, int quantity) throws KiteException, JSONException, IOException {
        Map<String, Object> params = new HashMap<>();
        params.put("tradingsymbol", tradingSymbol);
        params.put("exchange", exchange);
        params.put("transaction_type", transactionType);
        params.put("position_type", positionType);
        params.put("old_product", oldProduct);
        params.put("new_product", newProduct);
        params.put("quantity", quantity);

        return RequestHandler.putRequest(routes.get("portfolio.positions.modify"), params, enctoken);
    }

    /**
     * Retrieves list of market instruments available to trade.
     *
     * 	 Response is array for objects. For example,
     * 	{
     * 		instrument_token: '131098372',
     *		exchange_token: '512103',
     *		tradingsymbol: 'NIDHGRN',
     *		name: 'NIDHI GRANITES',
     *		last_price: '0.0',
     *		expiry: '',
     *		strike: '0.0',
     *		tick_size: '0.05',
     *		lot_size: '1',
     *		instrument_type: 'EQ',
     *		segment: 'BSE',
     *		exchange: 'BSE' }, ...]
     * @return List of instruments which are available to trade.
     * @throws KiteException is thrown for all Kite trade related errors.
     * @throws IOException is thrown when there is connection related errors.
     */
    public List<Instrument> getInstruments() throws KiteException, IOException, JSONException {
        return readCSV(RequestHandler.getCSVRequest(routes.get("market.instruments.all"), enctoken));
    }

    /**
     * Retrieves list of market instruments available to trade for an exchange
     *
     * 	 Response is array for objects. For example,
     * 	{
     * 		instrument_token: '131098372',
     *		exchange_token: '512103',
     *		tradingsymbol: 'NIDHGRN',
     *		name: 'NIDHI GRANITES',
     *		last_price: '0.0',
     *		expiry: '',
     *		strike: '0.0',
     *		tick_size: '0.05',
     *		lot_size: '1',
     *		instrument_type: 'EQ',
     *		segment: 'BSE',
     *		exchange: 'BSE' }, ...]
     * @param exchange  Filter instruments based on exchange. exchange can be NSE, BSE, NFO, BFO, CDS, MCX.
     * @return List of instruments which are available to trade for an exchange.
     * @throws KiteException is thrown for all Kite trade related errors.
     * @throws JSONException is thrown when there is exception while parsing response.
     * @throws IOException is thrown when there is connection related error.
     */
    public List<Instrument> getInstruments(String exchange) throws KiteException, JSONException, IOException {
        return readCSV(RequestHandler.getCSVRequest(routes.get("market.instruments").replace(":exchange", exchange), enctoken));
    }

    /**
     * Retrieves quote and market depth for an instrument
     *
     * @param instruments is the array of tradingsymbol and exchange or instrument token. For example {NSE:NIFTY 50, BSE:SENSEX} or {256265, 265}
     *
     * @return Map of String and Quote.
     * @throws KiteException is thrown for all Kite trade related errors.
     * @throws JSONException is thrown when there is exception while parsing response.
     * @throws IOException is thrown when there is connection related error.
     */
    public Map<String, Quote> getQuote(String [] instruments) throws KiteException, JSONException, IOException {
        JSONObject jsonObject = RequestHandler.getRequest(routes.get("market.quote"), "i", instruments, enctoken);
        Type type = new TypeToken<Map<String, Quote>>(){}.getType();
        return gson.fromJson(String.valueOf(jsonObject.get("data")), type);
    }

    /** Retrieves OHLC and last price.
     * User can either pass exchange with tradingsymbol or instrument token only. For example {NSE:NIFTY 50, BSE:SENSEX} or {256265, 265}
     * @return Map of String and OHLCQuote.
     * @param instruments is the array of tradingsymbol and exchange or instruments token.
     * @throws KiteException is thrown for all Kite trade related errors.
     * @throws IOException is thrown when there is connection related error.
     * */
    public Map<String, OHLCQuote> getOHLC(String [] instruments) throws KiteException, IOException, JSONException {
        JSONObject resp = RequestHandler.getRequest(routes.get("quote.ohlc"), "i", instruments, enctoken);
        Type type = new TypeToken<Map<String, OHLCQuote>>(){}.getType();
        return gson.fromJson(String.valueOf(resp.get("data")), type);
    }

    /** Retrieves last price.
     * User can either pass exchange with tradingsymbol or instrument token only. For example {NSE:NIFTY 50, BSE:SENSEX} or {256265, 265}.
     * @return Map of String and LTPQuote.
     * @param instruments is the array of tradingsymbol and exchange or instruments token.
     * @throws KiteException is thrown for all Kite trade related errors.
     * @throws IOException is thrown when there is connection related error.
     * */
    public Map<String, LTPQuote> getLTP(String[] instruments) throws KiteException, IOException, JSONException {
        JSONObject response = RequestHandler.getRequest(routes.get("quote.ltp"), "i", instruments, enctoken);
        Type type = new TypeToken<Map<String, LTPQuote>>(){}.getType();
        return gson.fromJson(String.valueOf(response.get("data")), type);
    }

    /**
     * Retrieves buy or sell trigger range for Cover Orders.
     * @return TriggerRange object is returned.
     * @param instruments is the array of tradingsymbol and exchange or instrument token.
     * @param transactionType "BUY or "SELL".
     * @throws KiteException is thrown for all Kite trade related errors.
     * @throws JSONException is thrown when there is exception while parsing response.
     * @throws IOException is thrown when there is connection related error.
     */
    public Map<String, TriggerRange> getTriggerRange(String[] instruments, String transactionType) throws KiteException, JSONException, IOException {
        String url = routes.get("market.trigger_range").replace(":transaction_type", transactionType.toLowerCase());
        JSONObject response = RequestHandler.getRequest(url, "i", instruments, enctoken);
        Type type = new TypeToken<Map<String, TriggerRange>>(){}.getType();
        return gson.fromJson(String.valueOf(response.get("data")), type);
    }

    /** Retrieves historical data for an instrument.
     * @param from "yyyy-mm-dd" for fetching candles between days and "yyyy-mm-dd hh:mm:ss" for fetching candles between timestamps.
     * @param to "yyyy-mm-dd" for fetching candles between days and "yyyy-mm-dd hh:mm:ss" for fetching candles between timestamps.
     * @param continuous set to true for fetching continuous data of expired instruments.
     * @param interval can be minute, day, 3minute, 5minute, 10minute, 15minute, 30minute, 60minute.
     * @param token is instruments token.
     * @param oi set to true for fetching open interest data. The default value is 0.
     * @return HistoricalData object which contains list of historical data termed as dataArrayList.
     * @throws KiteException is thrown for all Kite trade related errors.
     * @throws IOException is thrown when there is connection related error.
     * */
    public HistoricalData getHistoricalData(Date from, Date to, String token, String interval, boolean continuous, boolean oi) throws KiteException, IOException, JSONException {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Map<String, Object> params = new HashMap<>();
        params.put("from", format.format(from));
        params.put("to", format.format(to));
        params.put("continuous", continuous ? 1 : 0);
        params.put("oi", oi ? 1 : 0);

        String url = routes.get("market.historical").replace(":instrument_token", token).replace(":interval", interval);
        HistoricalData historicalData = new HistoricalData();
        historicalData.parseResponse(RequestHandler.getRequest(url, params, enctoken));
        return historicalData;
    }

    /** Retrieves mutualfunds instruments.
     * @return returns list of mutual funds instruments.
     * @throws KiteException is thrown for all Kite trade related errors.
     * @throws IOException is thrown when there is connection related errors.
     * */
    public List<MFInstrument> getMFInstruments() throws KiteException, IOException, JSONException {
        return readMfCSV(RequestHandler.getCSVRequest(routes.get("mutualfunds.instruments"), enctoken));
    }

    /** Place a mutualfunds order.
     * @return MFOrder object contains only orderId.
     * @param tradingsymbol Tradingsymbol (ISIN) of the fund.
     * @param transactionType BUY or SELL.
     * @param amount Amount worth of units to purchase. Not applicable on SELLs.
     * @param quantity Quantity to SELL. Not applicable on BUYs. If the holding is less than minimum_redemption_quantity, all the units have to be sold.
     * @param tag An optional tag to apply to an order to identify it (alphanumeric, max 8 chars).
     * @throws KiteException is thrown for all Kite trade related errors.
     * @throws IOException is thrown when there is connection related error.
     * */
    public MFOrder placeMFOrder(String tradingsymbol, String transactionType, double amount, double quantity, String tag) throws KiteException, IOException, JSONException {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("tradingsymbol", tradingsymbol);
        params.put("transaction_type", transactionType);
        params.put("amount", amount);
        if(transactionType.equals(Constants.TRANSACTION_TYPE_SELL)) params.put("quantity", quantity);
        params.put("tag", tag);

        JSONObject response = RequestHandler.postRequest(routes.get("mutualfunds.orders.place"), params, enctoken);
        MFOrder MFOrder = new MFOrder();
        MFOrder.orderId = response.getJSONObject("data").getString("order_id");
        return MFOrder;
    }

    /** If cancel is successful then api will respond as 200 and send back true else it will be sent back to user as KiteException.
     * @return true if api call is successful.
     * @param orderId is the order id of the mutualfunds order.
     * @throws KiteException is thrown for all Kite trade related errors.
     * @throws IOException is thrown when there connection related error.
     * */
    public boolean cancelMFOrder(String orderId) throws KiteException, IOException, JSONException {
        RequestHandler.deleteRequest(routes.get("mutualfunds.cancel_order").replace(":order_id", orderId), new HashMap<String, Object>(), enctoken);
        return true;
    }

    /** Retrieves all mutualfunds orders.
     * @return List of all the mutualfunds orders.
     * @throws KiteException is thrown for all Kite trade related errors.
     * @throws IOException is thrown when there is connection related error.
     * */
    public List<MFOrder> getMFOrders() throws KiteException, IOException, JSONException {
        JSONObject response = RequestHandler.getRequest(routes.get("mutualfunds.orders"), enctoken);
        return Arrays.asList(gson.fromJson(String.valueOf(response.get("data")), MFOrder[].class));
    }

    /** Retrieves individual mutualfunds order.
     * @param orderId is the order id of a mutualfunds scrip.
     * @return returns a single mutualfunds object with all the parameters.
     * @throws KiteException is thrown for all Kite trade related errors.
     * @throws IOException is thrown when there is connection related error.
     * */
    public MFOrder getMFOrder(String orderId) throws KiteException, IOException, JSONException {
        JSONObject response = RequestHandler.getRequest(routes.get("mutualfunds.order").replace(":order_id", orderId), enctoken);
        return gson.fromJson(response.get("data").toString(), MFOrder.class);
    }

    /** Place a mutualfunds sip.
     * @param tradingsymbol Tradingsymbol (ISIN) of the fund.
     * @param frequency weekly, monthly, or quarterly.
     * @param amount Amount worth of units to purchase. It should be equal to or greated than minimum_additional_purchase_amount and in multiple of purchase_amount_multiplier in the instrument master.
     * @param installmentDay If Frequency is monthly, the day of the month (1, 5, 10, 15, 20, 25) to trigger the order on.
     * @param instalments Number of installments to trigger. If set to -1, instalments are triggered at fixed intervals until the SIP is cancelled.
     * @param initialAmount Amount worth of units to purchase before the SIP starts. Should be equal to or greater than minimum_purchase_amount and in multiple of purchase_amount_multiplier. This is only considered if there have been no prior investments in the target fund.
     * @return MFSIP object which contains sip id and order id.
     * @throws KiteException is thrown for all Kite trade related errors.
     * @throws IOException is thrown when there is connection related error.
     * */
    public MFSIP placeMFSIP(String tradingsymbol, String frequency, int installmentDay, int instalments, int initialAmount, double amount) throws KiteException, IOException, JSONException {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("tradingsymbol", tradingsymbol);
        params.put("frequency", frequency);
        params.put("instalment_day", installmentDay);
        params.put("instalments", instalments);
        params.put("initial_amount", initialAmount);
        params.put("amount", amount);

        MFSIP MFSIP = new MFSIP();
        JSONObject response = RequestHandler.postRequest(routes.get("mutualfunds.sips.place"),params, enctoken);
        MFSIP.orderId = response.getJSONObject("data").getString("order_id");
        MFSIP.sipId = response.getJSONObject("data").getString("sip_id");
        return MFSIP;
    }

    /** Modify a mutualfunds sip.
     * @param frequency weekly, monthly, or quarterly.
     * @param status Pause or unpause an SIP (active or paused).
     * @param amount Amount worth of units to purchase. It should be equal to or greated than minimum_additional_purchase_amount and in multiple of purchase_amount_multiplier in the instrument master.
     * @param day If Frequency is monthly, the day of the month (1, 5, 10, 15, 20, 25) to trigger the order on.
     * @param instalments Number of instalments to trigger. If set to -1, instalments are triggered at fixed intervals until the SIP is cancelled.
     * @param sipId is the id of the sip.
     * @return returns true, if modify sip is successful else exception is thrown.
     * @throws KiteException is thrown for all Kite trade related errors.
     * @throws IOException is thrown when there is connection related error.
     * */
    public boolean modifyMFSIP(String frequency, int day, int instalments, double amount, String status, String sipId) throws KiteException, IOException, JSONException {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("frequency", frequency);
        params.put("day", day);
        params.put("instalments", instalments);
        params.put("amount", amount);
        params.put("status", status);

        RequestHandler.putRequest(routes.get("mutualfunds.sips.modify").replace(":sip_id", sipId), params, enctoken);
        return true;
    }

    /** Cancel a mutualfunds sip.
     * @param sipId is the id of mutualfunds sip.
     * @return returns true, if cancel sip is successful else exception is thrown.
     * @throws KiteException is thrown for all Kite trade related errors.
     * @throws IOException is thrown when there is connection related error.
     * */
    public boolean cancelMFSIP(String sipId) throws KiteException, IOException, JSONException {
        RequestHandler.deleteRequest(routes.get("mutualfunds.sip").replace(":sip_id", sipId), new HashMap<String, Object>(), enctoken);
        return true;
    }

    /** Retrieve all mutualfunds sip.
     * @return List of sips.
     * @throws KiteException is thrown for all Kite trade related errors.
     * @throws IOException is thrown when there is connection related error.
     * */
    public List<MFSIP> getMFSIPs() throws KiteException, IOException, JSONException {
        JSONObject response = RequestHandler.getRequest(routes.get("mutualfunds.sips"), enctoken);
        return Arrays.asList(gson.fromJson(String.valueOf(response.get("data")), MFSIP[].class));
    }

    /** Retrieve an individual sip.
     * @param sipId is the id of a particular sip.
     * @return MFSIP object which contains all the details of the sip.
     * @throws KiteException is thrown for all Kite trade related errors.
     * @throws IOException is thrown when there is connection related error.
     * */
    public MFSIP getMFSIP(String sipId) throws KiteException, IOException, JSONException {
        JSONObject response = RequestHandler.getRequest(routes.get("mutualfunds.sip").replace(":sip_id", sipId), enctoken);
        return gson.fromJson(response.get("data").toString(), MFSIP.class);
    }

    /** Retrieve all the mutualfunds holdings.
     * @return List of mutualfunds holdings.
     * @throws KiteException is thrown for all Kite trade related errors.
     * @throws IOException is thrown when there is connection related error.
     * */
    public List<MFHolding> getMFHoldings() throws KiteException, IOException, JSONException {
        JSONObject response = RequestHandler.getRequest(routes.get("mutualfunds.holdings"), enctoken);
        return Arrays.asList(gson.fromJson(String.valueOf(response.get("data")), MFHolding[].class));
    }

    
    /**This method parses csv and returns instrument list.
     * @param input is csv string.
     * @return  returns list of instruments.
     * @throws IOException is thrown when there is connection related error.
     * */
    private List<Instrument> readCSV(String input) throws IOException {
        ICsvBeanReader beanReader = null;
        File temp = File.createTempFile("tempfile", ".tmp");
        BufferedWriter bw = new BufferedWriter(new FileWriter(temp));
        bw.write(input);
        bw.close();

        beanReader = new CsvBeanReader(new FileReader(temp), CsvPreference.STANDARD_PREFERENCE);
        String[] header = beanReader.getHeader(true);
        CellProcessor[] processors = getProcessors();
        Instrument instrument;
        List<Instrument> instruments = new ArrayList<>();
        while((instrument = beanReader.read(Instrument.class, header, processors)) != null ) {
            instruments.add(instrument);
        }
        return instruments;
    }

    /**This method parses csv and returns instrument list.
     * @param input is mutualfunds csv string.
     * @return  returns list of mutualfunds instruments.
     * @throws IOException is thrown when there is connection related error.
     * */
    private List<MFInstrument> readMfCSV(String input) throws IOException{
        ICsvBeanReader beanReader = null;
        File temp = File.createTempFile("tempfile", ".tmp");
        BufferedWriter bw = new BufferedWriter(new FileWriter(temp));
        bw.write(input);
        bw.close();

        beanReader = new CsvBeanReader(new FileReader(temp), CsvPreference.STANDARD_PREFERENCE);
        String[] header = beanReader.getHeader(true);
        CellProcessor[] processors = getMfProcessors();
        MFInstrument instrument;
        List<MFInstrument> instruments = new ArrayList<>();
        while((instrument = beanReader.read(MFInstrument.class, header, processors)) != null ) {
            instruments.add(instrument);
        }
        return instruments;
    }

    /** This method returns array of cellprocessor for parsing csv.
     * @return CellProcessor[] array
     * */
    private CellProcessor[] getProcessors(){
        CellProcessor[] processors = new CellProcessor[]{
                new NotNull(new ParseLong()),   //instrument_token
                new NotNull(new ParseLong()),   //exchange_token
                new NotNull(),                  //trading_symbol
                new org.supercsv.cellprocessor.Optional(),                 //company name
                new NotNull(new ParseDouble()), //last_price
                new org.supercsv.cellprocessor.Optional(new ParseDate("yyyy-MM-dd")),                 //expiry
                new org.supercsv.cellprocessor.Optional(),                 //strike
                new NotNull(new ParseDouble()), //tick_size
                new NotNull(new ParseInt()),    //lot_size
                new NotNull(),                  //instrument_type
                new NotNull(),                  //segment
                new NotNull()                   //exchange
        };
        return processors;
    }

    /** This method returns array of cellprocessor for parsing mutual funds csv.
     * @return CellProcessor[] array
     * */
    private CellProcessor[] getMfProcessors(){
        CellProcessor[] processors = new CellProcessor[]{
                new org.supercsv.cellprocessor.Optional(),                  //tradingsymbol
                new org.supercsv.cellprocessor.Optional(),                  //amc
                new org.supercsv.cellprocessor.Optional(),                  //name
                new org.supercsv.cellprocessor.Optional(new ParseBool()),    //purchase_allowed
                new org.supercsv.cellprocessor.Optional(new ParseBool()),    //redemption_allowed
                new org.supercsv.cellprocessor.Optional(new ParseDouble()), //minimum_purchase_amount
                new org.supercsv.cellprocessor.Optional(new ParseDouble()), //purchase_amount_multiplier
                new org.supercsv.cellprocessor.Optional(new ParseDouble()), //minimum_additional_purchase_amount
                new org.supercsv.cellprocessor.Optional(new ParseDouble()), //minimum_redemption_quantity
                new org.supercsv.cellprocessor.Optional(new ParseDouble()), //redemption_quantity_multiplier
                new org.supercsv.cellprocessor.Optional(),                  //dividend_type
                new org.supercsv.cellprocessor.Optional(),                  //scheme_type
                new org.supercsv.cellprocessor.Optional(),                  //plan
                new org.supercsv.cellprocessor.Optional(),                  //settlement_type
                new org.supercsv.cellprocessor.Optional(new ParseDouble()), //last_price
                new org.supercsv.cellprocessor.Optional(new ParseDate("yyyy-MM-dd"))                   //last_price_date
        };
        return processors;
    }

}