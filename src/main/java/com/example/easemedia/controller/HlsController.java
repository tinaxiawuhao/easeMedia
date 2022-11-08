package com.example.easemedia.controller;

import cn.hutool.core.io.IORuntimeException;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.MD5;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.easemedia.common.AjaxResult;
import com.example.easemedia.entity.dto.CameraDto;
import com.example.easemedia.entity.Camera;
import com.example.easemedia.mapper.CameraMapper;
import com.example.easemedia.service.HlsService;
import com.example.easemedia.entity.vo.CameraVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * hls接口
 *
 */
@Slf4j
@RestController
public class HlsController {
	
	@Autowired
	private HlsService hlsService;
	@Autowired
	private CameraMapper cameraMapper;

	/**
	 * ts接收接口（回传，这里只占用网络资源，避免使用硬盘资源）
	 */
	@RequestMapping("record/{mediaKey}/{tsname}")
	public void name(HttpServletRequest request, @PathVariable("mediaKey") String mediaKey,
			@PathVariable("tsname") String tsname) {
		
		try {
			if(tsname.contains("m3u8")) {
				hlsService.processHls(mediaKey, request.getInputStream());
			} else {
				hlsService.processTs(mediaKey, tsname, request.getInputStream());
			}
		} catch (IORuntimeException | IOException e) {
			e.printStackTrace();
		}
	}
	

	@RequestMapping("ts/{cameraId}/{tsName}")
	public void getts(HttpServletResponse response, @PathVariable("cameraId") String mediaKey,
			@PathVariable("tsName") String tsName) throws IOException {
		
		String tsKey = mediaKey.concat("-").concat(tsName);
		byte[] bs = HlsService.cacheTs.get(tsKey);
		if(null == bs) {
			response.setContentType("application/json");
			response.getOutputStream().write("尚未生成ts".getBytes(StandardCharsets.UTF_8));
			response.getOutputStream().flush();
		} else {
			response.getOutputStream().write(bs);
			response.setContentType("video/mp2t");
			response.getOutputStream().flush();
		}
		
	}
	
	/**
	 * hls播放接口
	 */
	@RequestMapping("hls")
	public void video(CameraDto cameraDto, HttpServletResponse response)
			throws IOException {
		if (StrUtil.isBlank(cameraDto.getUrl())) {
			response.setContentType("application/json");
			response.getOutputStream().write("参数有误".getBytes(StandardCharsets.UTF_8));
			response.getOutputStream().flush();
		} else {
			String mediaKey = MD5.create().digestHex(cameraDto.getUrl());
			byte[] hls = HlsService.cacheM3u8.get(mediaKey);
			if(null == hls) {
				response.setContentType("application/json");
				response.getOutputStream().write("尚未生成m3u8".getBytes(StandardCharsets.UTF_8));
				response.getOutputStream().flush();
			} else {
				response.setContentType("application/vnd.apple.mpegurl");// application/x-mpegURL //video/mp2t ts;
				response.getOutputStream().write(hls);
				response.getOutputStream().flush();
			}
		}

	}
	
	/**
	 * 关闭切片
	 */
	@RequestMapping("stopHls")
	public AjaxResult stop(CameraVo cameraVo) {
		String digestHex = MD5.create().digestHex(cameraVo.getUrl());
		CameraDto cameraDto = new CameraDto();
		cameraDto.setUrl(cameraVo.getUrl());
		cameraDto.setMediaKey(digestHex);
		
		Camera camera = new Camera();
		camera.setHls(0);
		QueryWrapper<Camera> queryWrapper = new QueryWrapper<>();
		queryWrapper.eq("media_key", digestHex);
		cameraMapper.update(camera, queryWrapper);
		
		hlsService.closeConvertToHls(cameraDto);
		return AjaxResult.success("停止切片成功");
	}
	
	/**
	 * 开启切片
	 */
	@RequestMapping("startHls")
	public AjaxResult start(CameraVo cameraVo) {
		String digestHex = MD5.create().digestHex(cameraVo.getUrl());
		CameraDto cameraDto = new CameraDto();
		cameraDto.setUrl(cameraVo.getUrl());
		cameraDto.setMediaKey(digestHex);
		
		boolean startConvertToHls = hlsService.startConvertToHls(cameraDto);
		
		if(startConvertToHls) {
			Camera camera = new Camera();
			QueryWrapper<Camera> queryWrapper = new QueryWrapper<>();
			queryWrapper.eq("media_key", digestHex);
			camera.setHls(1);
			cameraMapper.update(camera, queryWrapper);
		}
		
		return AjaxResult.success("开启切片成功", startConvertToHls);
	}
	
}
