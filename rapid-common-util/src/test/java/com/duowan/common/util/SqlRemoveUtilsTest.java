package com.duowan.common.util;

import com.duowan.common.util.SqlRemoveUtils;

import junit.framework.TestCase;

public class SqlRemoveUtilsTest extends TestCase {
	
	public void testRemoveXsqlBuilderOrders() {
		String result = SqlRemoveUtils.removeXsqlBuilderOrders("where /~ order by [sortColumn] [sortDirection] ~/");
		assertEquals("where ",result);
		
		result = SqlRemoveUtils.removeXsqlBuilderOrders("where /~    oRder BY [sortColumn] [sortDirection] ~/");
		assertEquals("where ",result);
		
		result = SqlRemoveUtils.removeXsqlBuilderOrders("where order by [sortColumn] [sortDirection]");
		assertEquals("where ",result);
	}
}
