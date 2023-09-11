package org.example.bean;

import lombok.Data;

@Data
public class JsonResult {

    private int resultCode = 0;

    private String resultMsg;
    private Object resultData;

}
