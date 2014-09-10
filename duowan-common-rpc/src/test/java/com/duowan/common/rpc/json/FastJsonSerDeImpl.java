package com.duowan.common.rpc.json;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.DeserializationConfig.Feature;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.type.TypeFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSON;
import com.duowan.common.rpc.SerDe;
import com.duowan.common.rpc.SerializeException;

/**
 * 序列化的Json实现
 * 
 * @author badqiu
 * 
 */
public class FastJsonSerDeImpl implements SerDe {

	private static final Logger logger = LoggerFactory.getLogger(JsonSerDeImpl.class);

	public void serialize(Object object, OutputStream output,Map<String,Object> params) throws SerializeException {
		try {
			byte[] bytes = JSON.toJSONBytes(object);
			output.write(bytes);
		} catch (IOException e) {
			throw new SerializeException(e);
		}
	}

	public Object deserialize(InputStream input, Type returnType,Map<String,Object> params) throws SerializeException {
		try {
			return JSON.parseObject(IOUtils.toByteArray(input), returnType);
		} catch (IOException e) {
			throw new SerializeException(e);
		}
	}

}
