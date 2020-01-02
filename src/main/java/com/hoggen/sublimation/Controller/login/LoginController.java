package com.hoggen.sublimation.Controller.login;


import com.hoggen.sublimation.dto.*;
import com.hoggen.sublimation.entity.User;
import com.hoggen.sublimation.enums.LoginStateEnum;
import com.hoggen.sublimation.enums.UserStateEnum;
import com.hoggen.sublimation.service.httpsevice.Impl.RedisService;
import com.hoggen.sublimation.service.httpsevice.LoginService;
import com.hoggen.sublimation.service.httpsevice.UserService;
import com.hoggen.sublimation.util.*;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.imageio.ImageIO;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.google.code.kaptcha.Constants;
import com.google.code.kaptcha.Producer;
import sun.misc.BASE64Encoder;


@Controller
@RequestMapping(value="/api/login")
@Api(tags = "用户管理模块")
@Slf4j
public class LoginController {


    @Autowired
    private LoginService loginService;

    @Autowired
    private UserService userService;

    @Autowired
    private Producer captchaProducer;

    @Autowired
    private RedisService redisService;


    @RequestMapping(value = "/userLogin", method = RequestMethod.POST)
    @ApiOperation(value = "用户登录")
    @ResponseBody
    private Map<String, Object> userLogin(@Validated @RequestBody LoginDTO loginDTO ) {

        return loginService.userLogin(loginDTO.getPhone(),loginDTO.getPassword());
    }


    @RequestMapping(value = "/info", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(value = "用户信息获取")
    private Map<String, Object> userInfo(HttpServletRequest request) {
        String token = request.getHeader("token");
        String userId = request.getHeader("userId");
        Map<String, Object> modelMap = new HashMap<String, Object>();
        User user = userService.queryByUserId(userId);
        if (user == null) {
            return ResponedUtils.returnCode(UserStateEnum.EMPTY.getState(),UserStateEnum.EMPTY.getStateInfo(),"");
        }
        return ResponedUtils.returnCode(UserStateEnum.SUCCESS.getState(),UserStateEnum.SUCCESS.getStateInfo(),new ReturnUserDTO(user));
    }

    @RequestMapping(value = "/register", method = RequestMethod.POST)
    @ApiOperation(value = "用户注册")
    @ResponseBody
    private Map<String, Object> register(HttpServletRequest request,@Validated @RequestBody RegisterDTO registerDTO)  {
        Map<String, Object> modelMap = new HashMap<String, Object>();
//        if (!CodeJudgeUtil.codeJudge(request,registerDTO.getCode())){
//            return ResponedUtils.returnCode(LoginStateEnum.CODEERROR.getState(),LoginStateEnum.CODEERROR.getStateInfo(),modelMap);
//        }
        User user = new User();
        user.setPassword(registerDTO.getPassword());
        user.setMobile(registerDTO.getPhone());
        user.setUserName(registerDTO.getName());
        user.setCodeName(registerDTO.getCodeName());
        UserExecution effect = userService.insertUser(user);
        if (effect.getState() != 0){
            return ResponedUtils.returnCode(effect.getState(),effect.getStateInfo(),modelMap);
        }

        return ResponedUtils.returnCode(effect.getState(),effect.getStateInfo(),new ReturnUserDTO(effect.getUser()));
    }

    @RequestMapping(value = "/quit", method = RequestMethod.POST)
    @ApiOperation(value = "用户退出登录")
    @ResponseBody
    private Map<String, Object> quit(@Validated @RequestBody QuitDTO quitDTO)  {

        return loginService.quit(quitDTO);
    }


    @ApiOperation(value = "使用旧密码更新密码")
    @RequestMapping(value = "/updatePassword", method = RequestMethod.POST)
    @ResponseBody
    private Map<String, Object> updatePassword(HttpServletRequest request,@Validated @RequestBody UpdatePasswordDTO updatePasswordDTO) {
        Map<String, Object> modelMap = new HashMap<String, Object>();
//        String newPassword = HttpServletRequestUtil.getString(request,"newPassword");
//        String oldPassword = HttpServletRequestUtil.getString(request,"oldPassword");
//        if (newPassword == null ||  oldPassword == null) {
//            return ResponedUtils.returnCode(LoginStateEnum.INNERERROR.getState(),LoginStateEnum.INNERERROR.getStateInfo(),modelMap);
//        }
        String userId = request.getHeader("userId");
        User user = userService.queryByUserId(userId);
        if (user == null || user.getStatus() == 0) {
            return ResponedUtils.returnCode(LoginStateEnum.EMPTY.getState(),LoginStateEnum.EMPTY.getStateInfo(),modelMap);

        }
        if (user != null && user.getPassword() != null) {
            if ((MD5Util.MD5Encode(updatePasswordDTO.getOldPassword() + user.getRandomString())).equals(user.getPassword())) {
                User newPasswordUser = new User();
                newPasswordUser.setUserId(userId);
                newPasswordUser.setPassword(updatePasswordDTO.getNewPassword());
                newPasswordUser.setUpdateTime(new Date());
                UserExecution effect = userService.modifyUser(newPasswordUser);
                return ResponedUtils.returnCode(effect.getState(),effect.getStateInfo(),modelMap);

            }
            else  {
                return ResponedUtils.returnCode(LoginStateEnum.OLDPASSWORDERROR.getState(),LoginStateEnum.OLDPASSWORDERROR.getStateInfo(),modelMap);
            }
        }else {
            return ResponedUtils.returnCode(LoginStateEnum.EMPTY.getState(),LoginStateEnum.EMPTY.getStateInfo(),modelMap);
        }

    }
    @ApiOperation(value = "更新头像")
    @RequestMapping(value = "/updateAvatar", method = RequestMethod.GET)
    @ResponseBody
    private Map<String, Object> updateAvatar(HttpServletRequest request,@Validated @RequestBody UpdateAvatarDTO updateAvatarDTO) {
        Map<String, Object> modelMap = new HashMap<String, Object>();
        String userId = request.getHeader("userId");
        User user = userService.queryByUserId(userId);
        if (user == null || user.getStatus() == 0) {
            return ResponedUtils.returnCode(LoginStateEnum.EMPTY.getState(),LoginStateEnum.EMPTY.getStateInfo(),modelMap);
        }
        User userAvartar = new User();
        userAvartar.setUserId(userId);
        userAvartar.setAvatar(updateAvatarDTO.getAvatar());

        UserExecution effect = userService.modifyUser(userAvartar);

        return ResponedUtils.returnCode(effect.getState(),effect.getStateInfo(),modelMap);
    }


    /**
     * 生成验证码
     * @param request
     * @param response
     * @throws Exception
     */
    @ApiOperation(value = "获取注册验证码")
    @RequestMapping(value = "/getKaptchaImage", method = RequestMethod.GET)
    @ResponseBody
    public Map<String, Object>  getKaptchaImage(HttpServletRequest request,
                                HttpServletResponse response) throws Exception {

        byte[] captchaChallengeAsJpeg = null;
        ByteArrayOutputStream jpegOutputStream = new ByteArrayOutputStream();
        // 生产验证码字符串并保存到session中
        String createText = captchaProducer.createText();
        request.getSession().setAttribute(Constants.KAPTCHA_SESSION_KEY, createText);
        // 使用生产的验证码字符串返回一个BufferedImage对象并转为byte写入到byte数组中
        BufferedImage challenge = captchaProducer.createImage(createText);
        ImageIO.write(challenge, "png", jpegOutputStream);
        // 对字节数组Base64编码
        BASE64Encoder encoder = new BASE64Encoder();
        Map<String, Object> modelMap = new HashMap<String, Object>();
        modelMap.put("img", encoder.encode(jpegOutputStream.toByteArray()));

        return ResponedUtils.returnCode(LoginStateEnum.SUCCESS.getState(),LoginStateEnum.SUCCESS.getStateInfo(),modelMap);

    }


}
