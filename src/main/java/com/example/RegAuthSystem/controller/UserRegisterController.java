package com.example.RegAuthSystem.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.RegAuthSystem.service.IClientService;
import com.example.RegAuthSystem.service.IRegisterAccountService;
import com.example.RegAuthSystem.service.dto.ClientDto;
import com.example.RegAuthSystem.service.dto.RegisterUserDto;
import com.example.RegAuthSystem.service.dto.RegistrationProgressRequestDto;
import com.example.RegAuthSystem.service.dto.UpdateUserProfileDto;

import jakarta.validation.Valid;

/**
 * 用戶管理 Controller
 * 
 * 該 Controller 負責處理與用戶相關的請求，包括用戶註冊、登錄、資料查詢等功能。
 */
@RestController
@RequestMapping("/user/register")
public class UserRegisterController {

	@Autowired
	private IClientService clientService;
	@Autowired
	private IRegisterAccountService registerAccountServiceImpl;

	/**
	 * 注冊新用戶 用戶通過此接口進行註冊，現階段只支援Email註冊。 狀況一: 初次登記會寄出驗證信件 狀況二:
	 * 已經寄出過驗證信件，上次的驗證信在未過期狀況下會回傳 400 Error 狀況三:
	 * 已經寄出過驗證信件，且上次的驗證信以過期，將更新過期時間與UUID重新寄信 POST 請求路徑：/user/register
	 * 
	 */
	@PostMapping
	public ResponseEntity<Object> registerUser(@RequestBody @Valid RegisterUserDto registerUserDto) {
		// 實現註冊功能的程式碼
		ClientDto clientDto = clientService.controllerFindByEmail(registerUserDto.getEmail());
		return registerAccountServiceImpl.registerOrValidateUser(clientDto);
	}

	/**
	 * 
	 * @param registrationVerificationCode
	 * @param clientInfoDto
	 * @return
	 */
	@PostMapping(value = "/profile/{verify_code}")
	public ResponseEntity<Object> updateUserProfile(
			@PathVariable(name = "verify_code") String registrationVerificationCode,
			@RequestBody UpdateUserProfileDto updateUserProfileDto) {
		ClientDto clientDto = clientService.findByRegistrationVerificationCode(registrationVerificationCode);
		return registerAccountServiceImpl.updateUserProfile(clientDto, updateUserProfileDto);
	}

	/**
	 * 申請"註冊進度"查詢 取得"註冊查詢進度"的驗證碼
	 * 
	 * @param emailVerifyDto
	 * @return
	 */
	@PostMapping(value = "/progress")
	public ResponseEntity<Object> requestRegistrationProgress(
			@RequestBody RegistrationProgressRequestDto registrationProgressRequestDto) {
		ClientDto clientDto = clientService.controllerFindByEmail(registrationProgressRequestDto.getEmail());
		return registerAccountServiceImpl.getRegistrationProgress(clientDto);
	}

	/**
	 * 註冊查詢進度
	 * 
	 * @param verifyCode
	 * @return
	 */
	@GetMapping(value = "/check-progress/{verify_code}")
	public ResponseEntity<Object> checkUserRegistrationProgress(
			@PathVariable(name = "verify_code") String registrationProgressVerificationCode) {
		return registerAccountServiceImpl.checkUserRegistrationProgress(registrationProgressVerificationCode);
	}
}
