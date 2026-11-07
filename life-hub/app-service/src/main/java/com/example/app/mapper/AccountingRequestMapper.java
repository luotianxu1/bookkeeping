package com.example.app.mapper;

import org.apache.ibatis.annotations.*;

public interface AccountingRequestMapper {
    record StoredRequest(String requestHash, String responseJson) {}

    @Select("SELECT request_hash AS requestHash, response_json AS responseJson FROM accounting_requests "
        + "WHERE user_id=#{userId} AND operation=#{operation} AND request_id=#{requestId}")
    StoredRequest find(@Param("userId") Long userId, @Param("operation") String operation, @Param("requestId") String requestId);

    @Insert("INSERT INTO accounting_requests(user_id,operation,request_id,request_hash,response_json) "
        + "VALUES(#{userId},#{operation},#{requestId},#{hash},#{response})")
    int insert(@Param("userId") Long userId, @Param("operation") String operation, @Param("requestId") String requestId,
        @Param("hash") String hash, @Param("response") String response);
}
