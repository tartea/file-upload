package org.example.bean;

import lombok.Data;

@Data
public class MergeDTO {

    String fileName;
    String fileMd5;
    Integer chunks;
}
