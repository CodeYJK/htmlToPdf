package com.pdf.htmltopdf;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import java.util.*;

public class JSONUtils {
    public static Map<String, Object> parseJson2Map(String jsonStr) {
        Map<String, Object> hashedMap = new HashMap<>();
        Map<String, Object> map = null;
        try {
            map = (Map<String, Object>) JSONObject.parseObject(jsonStr, Map.class);
        } catch (Exception ex) {
            hashedMap = new HashMap<>();
            try {
                List<Map> list = JSONArray.parseArray(jsonStr, Map.class);
                hashedMap.put("REQ_LIST", list);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return hashedMap;
    }

    public static Object parseJSON2Obj(String jsonStr) {
        JSONObject jsonObj = JSONObject.parseObject(jsonStr);
        return parseJSON2Obj(jsonObj);
    }

    private static Object parseJSON2Obj(JSONObject jsonObj) {
        Set<String> set = jsonObj.keySet();
        Map<String, Object> retMap = new HashMap();
        Iterator<String> it = set.iterator();
        while (it.hasNext()) {
            String key = it.next();
            Object obj = null;
            Object jObj = jsonObj.get(key);
            if (jObj instanceof JSONObject) {
                if (!((JSONObject) jObj).isEmpty())
                    obj = parseJSON2Obj((JSONObject) jObj);
            } else if (jObj instanceof JSONArray) {
                obj = parseJSON2List(jObj.toString());
            } else {
                obj = jsonObj.get(key);
            }
            retMap.put(key.toUpperCase(), obj);
        }
        return retMap;
    }

    public static Object parseJSON2Obj(String jsonStr, String className) {
        JSONObject jsonObj = JSONObject.parseObject(jsonStr);
        return parseJSON2Obj(jsonObj, className);
    }

    private static Object parseJSON2Obj(JSONObject jsonObj, String className) {
        Class<?> pojoClass = null;
        try {
            pojoClass = Class.forName(className);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return JSONObject.toJavaObject((JSON) jsonObj, pojoClass);
    }

    public static List<Object> parseJSON2List(String jsonStr) {
        JSONArray jsonArray = JSONArray.parseArray(jsonStr);
        List<Object> list = new ArrayList();
        for (int index = 0; index < jsonArray.size(); index++) {
            Object obj = jsonArray.get(index);
            if (obj instanceof JSONObject) {
                Map<String, Object> retMap = (Map<String, Object>) parseJSON2Obj((JSONObject) obj);
                list.add(retMap);
            } else if (obj instanceof String) {
                list.add(String.valueOf(obj));
            }
        }
        return list;
    }

    public static List<Object> parseJSON2List(String jsonStr, String className) {
        JSONArray jsonArray = JSONArray.parseArray(jsonStr);
        List<Object> list = new ArrayList();
        for (int index = 0; index < jsonArray.size(); index++) {
            JSONObject jsonObj = jsonArray.getJSONObject(index);
            Object obj = parseJSON2Obj(jsonObj, className);
            list.add(obj);
        }
        return list;
    }

    public static String formObj2JSON(Object obj) {
        String jsonStr = JSONObject.toJSONString(obj);
        return jsonStr;
    }

    public static String formObj2JSON(List<?> listObj) {
        String jsonStr = JSONArray.toJSONString(listObj);
        return jsonStr;
    }

    public static List<Object> parseJSON(String jsonStr) {
        try {
            return parseJSON2List(jsonStr);
        } catch (Exception ex) {
            ex.printStackTrace();
            try {
                Object obj = parseJSON2Obj(jsonStr);
                List<Object> list = new ArrayList();
                list.add(obj);
                return list;
            } catch (Exception exception) {
                exception.printStackTrace();
                return null;
            }
        }
    }
}
