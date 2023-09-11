package org.example.controller;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.FileUtils;
import org.example.bean.JsonResult;
import org.example.bean.MergeDTO;
import org.example.bean.MultipartFileParam;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.*;

@RestController
@Slf4j
@RequestMapping(value = "/bigfile")
@CrossOrigin(origins = "*")
public class BigFileController {

    private  String fileStorePath = "D:\\file";

    /**
     * 判断文件是否上传过，是否存在分片，断点续传
     *
     * @param fileMd5
     * @return
     */
    @RequestMapping(value = "/check", method = RequestMethod.POST)
    public JsonResult checkBigFile(String fileMd5) {
        JsonResult jsonResult = new JsonResult();
        // 秒传
        File mergeMd5Dir = new File(fileStorePath + "/" + "merge" + "/" + fileMd5);
        if (mergeMd5Dir.exists()) {
            mergeMd5Dir.mkdirs();
            //文件已存在，下标为-1
            jsonResult.setResultCode(-1);
            return jsonResult;
        }

        // 读取目录里的所有文件
        File dir = new File(fileStorePath + "/" + fileMd5);
        File[] childs = dir.listFiles();
        if (childs == null) {
            //文件没有上传过，下标为零
            jsonResult.setResultCode(0);
        } else {
            //文件上传中断过，返回当前已经上传到的下标
            jsonResult.setResultCode(childs.length - 1);
        }
        return jsonResult;
    }

    /**
     * 上传文件
     *
     * @param request
     * @return
     * @throws Exception
     */
    @RequestMapping(value = "/upload", method = RequestMethod.POST)
    public JsonResult fileUpload(MultipartFileParam param, HttpServletRequest request) {
        boolean isMultipart = ServletFileUpload.isMultipartContent(request);
        JsonResult jsonResult = new JsonResult();

        if(StringUtils.isEmpty(param.getMd5())){
            jsonResult.setResultCode(-1);
            jsonResult.setResultMsg("md5值不存在");
            return jsonResult;
        }
        // 文件名
        String fileName = param.getName();
        // 文件每次分片的下标
        int chunkIndex = param.getChunk();
        if (isMultipart) {
            File file = new File(fileStorePath + "/" + param.getMd5());
            if (!file.exists()) {
                file.mkdir();
            }
            File chunkFile = new File(
                    fileStorePath + "/" + param.getMd5() + "/" + chunkIndex);
            try {
                FileUtils.copyInputStreamToFile(param.getFile().getInputStream(), chunkFile);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        log.info("文件-:{}的小标-:{},上传成功", fileName, chunkIndex);
        return jsonResult;
    }

    /**
     * 分片上传成功之后，合并文件
     *
     * @param mergeDTO
     * @return
     */
    @PostMapping(value = "/merge")
    public JsonResult fileMerge(MergeDTO mergeDTO) {

        if (StringUtils.isEmpty(mergeDTO.getFileMd5())
                || StringUtils.isEmpty(mergeDTO.getFileName())
                || Objects.isNull(mergeDTO.getChunks())) {
            JsonResult jsonResult = new JsonResult();
            jsonResult.setResultCode(-1);
            jsonResult.setResultMsg("参数不能为空");
            return jsonResult;
        }
        return mergeFileList(mergeDTO);
    }

    /**
     * 合并文件
     *
     * @param mergeDTO
     */
    private JsonResult mergeFileList(MergeDTO mergeDTO) {
        JsonResult jsonResult = new JsonResult();
        FileChannel outChannel = null;
        try {
            // 读取目录里的所有文件
            File dir = new File(fileStorePath + "/" + mergeDTO.getFileMd5());
            File[] childs = dir.listFiles();
            if (Objects.isNull(childs) || childs.length == 0) {
                jsonResult.setResultCode(-1);
                jsonResult.setResultMsg("文件不存在");
                return jsonResult;
            }
            if (!Objects.equals(childs.length, mergeDTO.getChunks())) {
                jsonResult.setResultCode(-1);
                jsonResult.setResultMsg("文件已损坏，请重新上传");
                return jsonResult;
            }
            // 转成集合，便于排序
            List<File> fileList = sortedFile(childs);

            final String tempFilePath = fileStorePath + "/" + "merge" + "/" + mergeDTO.getFileMd5();
            // 合并后的文件
            File outputFile = new File(tempFilePath + "/" + mergeDTO.getFileName());
            // 创建文件
            if (!outputFile.exists()) {
                File mergeMd5Dir = new File(tempFilePath);
                if (!mergeMd5Dir.exists()) {
                    mergeMd5Dir.mkdirs();
                }
                log.info("创建文件");
                outputFile.createNewFile();
            }
            outChannel = new FileOutputStream(outputFile).getChannel();
            FileChannel inChannel = null;
            try {
                for (File file : fileList) {
                    inChannel = new FileInputStream(file).getChannel();
                    inChannel.transferTo(0, inChannel.size(), outChannel);
                    inChannel.close();
                    // 删除分片
                    file.delete();
                }
            } catch (Exception e) {
                e.printStackTrace();
                //发生异常，文件合并失败 ，删除创建的文件
                outputFile.delete();
                dir.delete();//删除文件夹
            } finally {
                if (inChannel != null) {
                    inChannel.close();
                }
            }
            //删除分片所在的文件夹
            dir.delete();
            // FIXME: 数据库操作, 记录文件存档位置
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (outChannel != null) {
                    outChannel.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        jsonResult.setResultCode(0);
        return jsonResult;
    }

    /**
     * 对文件进行排序
     *
     * @param childs
     * @return
     */
    private static List<File> sortedFile(File[] childs) {
        List<File> fileList = new ArrayList<File>(Arrays.asList(childs));
        Collections.sort(fileList, (o1, o2) -> Integer.parseInt(o1.getName()) < Integer.parseInt(o2.getName()) ? -1 : 1);
        return fileList;
    }
}