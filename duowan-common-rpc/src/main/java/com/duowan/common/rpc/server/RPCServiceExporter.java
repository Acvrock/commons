package com.duowan.common.rpc.server;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationContextException;
import org.springframework.remoting.support.RemoteExporter;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.HttpRequestHandler;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.handler.AbstractUrlHandlerMapping;

import com.duowan.common.rpc.RPCResponse;
import com.duowan.common.rpc.SerDe;
import com.duowan.common.rpc.WebServiceException;
import com.duowan.common.rpc.json.JsonSerDeImpl;
import com.duowan.common.rpc.json.JsonpSerDeImpl;
import com.duowan.common.rpc.util.RequestParameterUtil;
import com.duowan.common.rpc.util.StringUtil;

public class RPCServiceExporter extends RemoteExporter implements HttpRequestHandler,HandlerMapping,ApplicationContextAware,InitializingBean {
	
	public static final String ENCODING = "UTF-8";
	private static Logger logger = LoggerFactory.getLogger(RPCServiceExporter.class);
	private static final String DEFAULT_FORMAT = "json";
	public static final String KEY_NO_WRAP = "__noWrapResult"; // 是否包装返回结果(true则返回 RPCResponse,false直接返回结果),默认值是false
	
	private ApplicationContext applicationContext;
	private String defaultFormat = DEFAULT_FORMAT;
	private Map<String,SerDe> serDeMapping = new HashMap<String,SerDe>();
	
	MethodInvoker invoker = new MethodInvoker();
	
	/**
	 * URL映射的目录前缀
	 */
	private String dir;	
	
	public void setSerDeMapping(Map<String, SerDe> serDeMapping) {
		this.serDeMapping = serDeMapping;
	}
	
	public String getDir() {
		return dir;
	}

	public void setDir(String dir) {
		if(dir != null) {
			Assert.isTrue(dir.startsWith("/"),"dir must be start with '/' prefix,example values:  /rpc,/service etc...");
		}
		this.dir = dir;
	}

	/**
	 * Processes the incoming RPC request and creates a RPC response.
	 */
	public void handleRequest(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

//		if (!"POST".equals(request.getMethod())) {
//			throw new HttpRequestMethodNotSupportedException(request.getMethod(),
//					new String[] {"POST"}, "RPCServiceExporter only supports POST requests");
//		}
		
		request.setCharacterEncoding(ENCODING);
		response.setCharacterEncoding(ENCODING);
		RPCContext.setRequest(request);
		RPCContext.setResponse(response);
		MDC.put("traceId", RandomStringUtils.randomAlphanumeric(10));
		
		Map<String, Object> parameters = RequestParameterUtil.getParameters(request);
		try {
			Object result = invokeServiceMethod(request,parameters);
			if("true".equals(parameters.get(KEY_NO_WRAP))) {
				serializeResult(result,request, response,parameters);
			}else {
				serializeResult(new RPCResponse(result),request, response,parameters);
			}
		} catch (WebServiceException e) {
			logger.warn("error on uri:"+request.getRequestURI(),e); // TODO 删除本日志打印,或者不打印error级别
			serializeResult(new RPCResponse(e.getErrorNo(),e.getMessage()),request, response,parameters);
		} catch(Throwable e){
			logger.warn("error on uri:"+request.getRequestURI(),e); // TODO 删除本日志打印,或者不打印error级别
			serializeResult(new RPCResponse(e.getClass().getName(),e.getMessage()),request, response,parameters);
		}finally {
			RPCContext.clear();
			MDC.remove("traceId");
		}
		
	}

	private void serializeResult(Object result,HttpServletRequest request, HttpServletResponse response,Map<String, Object> parameters) throws IOException {
//		response.setContentType("text/html"); // TODO 修正返回值类型，如 application/xml,application/json
		String format =  StringUtils.defaultIfEmpty(request.getParameter(MethodInvoker.KEY_FORMAT),defaultFormat);
		SerDe serDe = lookupSerDe(format);
		
//		sendHttpErrorIfIsNumber(result, response);
		
		serDe.serialize(result, response.getOutputStream(),parameters);
	}

	private SerDe lookupSerDe(String format) {
		SerDe serDe = serDeMapping.get(format);
		if(serDe == null) {
			throw new IllegalArgumentException("error "+MethodInvoker.KEY_FORMAT+"="+format+" correct is:"+serDeMapping.keySet());
		}
		return serDe;
	}

	private Object invokeServiceMethod(HttpServletRequest request,Map<String, Object> parameters) {
		try {
			String serviceId = resloveServiceId(request);
			String method = resloveMethod(request);
			
			return invoker.invoke(serviceId, method, parameters);
		} catch (WebServiceException e) {
			throw e;
		} catch(Exception e) {
			ReflectionUtils.handleReflectionException(e);
			return null;
		}
	}
	
	/**
	 * extract method from "/services/serviceId/method.do" 
	 * @param request
	 * @return
	 */
	private String resloveMethod(HttpServletRequest request) {
		String[] array = StringUtils.split(request.getRequestURI(), '/');
		if(array.length < 1) {
			throw new IllegalArgumentException("cannot reslove method from requestURI:"+request.getRequestURI());
		}
		String method = array[array.length - 1];
		return StringUtil.removeExtension(method); 
	}

	/**
	 * extract serviceId from "/services/serviceId/method" 
	 * @param request
	 * @return
	 */
	private String resloveServiceId(HttpServletRequest request) {
		String[] array = StringUtils.split(request.getRequestURI(), '/');
		if(array.length < 2) {
			throw new IllegalArgumentException("cannot reslove serviceId from requestURI:"+request.getRequestURI());
		}
		return array[array.length - 2];
	}

	public void setApplicationContext(ApplicationContext applicationContext)
			throws BeansException {
		this.applicationContext = applicationContext;
	}

	public void afterPropertiesSet() throws Exception {
		Assert.notNull(applicationContext,"'applicationContext' must be not null");
		if(serDeMapping == null || serDeMapping.isEmpty()) {
			Map<String,SerDe> defaultSerdeMapping = new HashMap<String,SerDe>();
			defaultSerdeMapping.put("json", new JsonSerDeImpl());
			defaultSerdeMapping.put("jsonp", new JsonpSerDeImpl());
			serDeMapping = defaultSerdeMapping;
		}
		Assert.notEmpty(serDeMapping,"'serDeMapping' must be not empty");
		
		logger.info("support serDeMapping:"+serDeMapping.keySet());
		
		initMethodInvoker();
		initRPCHandlerMapping();
	}
	
	private void initMethodInvoker() {
		invoker.addService(getServiceId(), getService(), getServiceInterface());
		logger.info("export RPC service:"+getServiceId()+" serviceInterface:"+getServiceInterface());
	}
	
	private String getServiceId() {
		return getServiceInterface().getSimpleName();
	}
	
	private RPCHandlerMapping handlerMapping;
	private void initRPCHandlerMapping() {
		handlerMapping = new RPCHandlerMapping(this);
		handlerMapping.setApplicationContext(applicationContext);
	}
	
	public HandlerExecutionChain getHandler(HttpServletRequest request)throws Exception {
		return handlerMapping.getHandler(request);
	}
	
	public class RPCHandlerMapping extends AbstractUrlHandlerMapping {
		private Object handler;
		
		public RPCHandlerMapping(Object handler) {
			super();
			this.handler = handler;
		}

		public void initApplicationContext() throws ApplicationContextException {
			super.initApplicationContext();
			String serviceId = getServiceId();
			Method[] methods = getServiceInterface().getMethods();
			for(Method method : methods) {
				String prefixDir = StringUtils.defaultString(dir);
				String urlPath = prefixDir + "/"+serviceId+"/"+method.getName();
				registerHandler(urlPath, handler);
			}
		}


	}
	
	
}
