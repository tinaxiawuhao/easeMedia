package com.example.easemedia.entity.vo;

import lombok.Data;

@Data
public class CameraVo {

	private String id;
	/**
	 * 播放地址
	 */
	private String url;
	
	/**
	 * 备注
	 */
	private String remark;
	
	/**
	 * 启用flv
	 */
	private boolean enabledFlv = false;
	
	/**
	 * 启用hls
	 */
	private boolean enabledHls = false;
	
	/**
	 * javacv/ffmpeg
	 */
	private String mode = "未开启";
}
