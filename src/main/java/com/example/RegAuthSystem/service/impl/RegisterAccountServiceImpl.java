package com.example.RegAuthSystem.service.impl;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.example.RegAuthSystem.mapper.ClientMapper;
import com.example.RegAuthSystem.service.IClientInfoService;
import com.example.RegAuthSystem.service.IClientService;
import com.example.RegAuthSystem.service.IEmailService;
import com.example.RegAuthSystem.service.IRegisterAccountService;
import com.example.RegAuthSystem.service.ISystemParameterSettingService;
import com.example.RegAuthSystem.service.dto.ClientDto;
import com.example.RegAuthSystem.service.dto.ClientInfoDto;
import com.example.common.config.HttpBody;
import com.example.common.config.MessageCode;
import com.example.common.config.ResponseResult;
import com.example.common.enums.ClientStatusEnum;
import com.example.orm.entity.Client;
import com.example.orm.entity.EmailTemplate;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class RegisterAccountServiceImpl implements IRegisterAccountService {

	@Autowired
	private IClientService clientService;
	@Autowired
	private ISystemParameterSettingService systemParameterSettingService;
	@Autowired
	private IEmailService emailService;
	@Autowired
	private EmailTemplateServiceImpl emailTemplateServiceImpl;
	@Autowired
	private IClientInfoService clientInfoService;


	/**
	 * 註冊新用戶和錯過驗證信的已註冊的用戶 依照帳號的狀態去處理對應的行為
	 * 
	 * @param clientDto
	 * @return
	 */
	@Override
	public ResponseEntity<Object> registerOrValidateUser(ClientDto clientDto) {
		Timestamp currentTime = Timestamp.from(Instant.now());
		String associatedApi = "register_user";
		Long emailExpirationTime = systemParameterSettingService.findEmailExpirationTime();
		// 初次登記會寄出驗證信件
		if (clientDto.getStatus() == ClientStatusEnum.NEW_REGISTRATION.getStatus()) {
			ClientDto newClientDto = clientService.createClient(clientDto.getEmail(), emailExpirationTime);
			return this.sendVerificationEmail(newClientDto.getEmail(), newClientDto.getRegistrationVerificationCode(),
					associatedApi) ? ResponseResult.ok(HttpBody.build(MessageCode.SUCCESS, null))
							: ResponseResult.failed(
									HttpBody.build(MessageCode.FAILED, "initiateRegistrationWithEmail is fail."));
		}

		// 已經寄出過驗證信件，上次的驗證信在未過期狀況下會回傳 403 Error
		if (clientDto.getStatus() == ClientStatusEnum.EMAIL_VERIFIED.getStatus()
				&& currentTime.before(clientDto.getRegistrationVerificationCodeExpiryTime())) {
			return ResponseResult.forbidden(HttpBody.build(MessageCode.Forbidden,
					"The last verification letter is still valid, so cancel sending again."));
		}

		// 已經寄出過驗證信件，且上次的驗證信以過期，將更新過期時間與UUID重新寄信
		if (clientDto != null && clientDto.getStatus().intValue() == ClientStatusEnum.EMAIL_VERIFIED.getStatus()
				&& currentTime.after(clientDto.getRegistrationVerificationCodeExpiryTime())) {
			clientService.updateClientRegistrationVerificationCodeExpiryTime(clientDto, emailExpirationTime);
			return this.sendVerificationEmail(clientDto.getEmail(), clientDto.getRegistrationVerificationCode(),
					associatedApi) ? ResponseResult.ok(HttpBody.build(MessageCode.SUCCESS, null))
							: ResponseResult.failed(HttpBody.build(MessageCode.FAILED,
									"updateExpiredVerificationCodeAndResendEmail is fail."));
		}

		// 如果帳號以存在且狀態在 20(BASIC_INFO_SUBMITTED)以上 代表帳號有註冊或已在使用
		if ((clientDto != null) && (clientDto.getStatus() >= ClientStatusEnum.BASIC_INFO_SUBMITTED.getStatus())) {
			return ResponseResult.validateArgsFailed(HttpBody.build(MessageCode.ACCOUNT_EXISTS,
					"The letter has entered the review stage. Please wait patiently."));
		}

		// 如果走到這邊就代表有出現沒想過的邊際
		return ResponseResult.failed(HttpBody.build(MessageCode.FAILED, "Please contact customer service"));

	}

	/**
	 * 寄送註冊驗證信
	 * 
	 * @param clientDto
	 * @param associatedApi
	 * @return
	 */
	private Boolean sendVerificationEmail(String email, String uuid, String associatedApi) {
		String emailSender = systemParameterSettingService.findEmailSender();
		String emailRedirectUrl = systemParameterSettingService
				.findParametersForEmailType("email_type_" + associatedApi);
		Optional<EmailTemplate> emailTemplate = emailTemplateServiceImpl.findByAssociatedApi(associatedApi);
		return emailService.sendEmailWithTemplateToUser(emailTemplate, associatedApi, email, uuid, emailSender,
				emailRedirectUrl);
	}

	/**
	 * 註冊查詢進度
	 */
	@Override
	public ResponseEntity<Object> checkUserRegistrationProgress(String registrationProgressVerificationCode) {
		ClientDto clientDto = clientService
				.findByRegistrationProgressVerificationCode(registrationProgressVerificationCode);
		Timestamp currentTime = Timestamp.from(Instant.now());

		if (clientDto == null) {
			return ResponseResult.validateArgsFailed(HttpBody.build(MessageCode.ACCOUNT_DOES_NOT_EXIST, null));
		}

		if (clientDto.getRegistrationProgressVerificationCode().isEmpty()
				&& currentTime.before(clientDto.getResetPasswordVerificationCodeExpiryTime())) {
			return ResponseResult.validateArgsFailed(HttpBody.build(MessageCode.VERIFY_EMAIL_STILL_VALID, null));

		}

		switch (ClientStatusEnum.getClientStatusEnum(clientDto.getStatus())) {
		case NEW_REGISTRATION:
			return ResponseResult
					.ok(HttpBody.build(MessageCode.SUCCESS, ClientStatusEnum.NEW_REGISTRATION.getDescription()));
		case EMAIL_VERIFIED:
			return ResponseResult
					.ok(HttpBody.build(MessageCode.SUCCESS, ClientStatusEnum.EMAIL_VERIFIED.getDescription()));
		case BASIC_INFO_SUBMITTED:
			return ResponseResult
					.ok(HttpBody.build(MessageCode.SUCCESS, ClientStatusEnum.BASIC_INFO_SUBMITTED.getDescription()));
		case ASSISTANT_REVIEWED:
			return ResponseResult
					.ok(HttpBody.build(MessageCode.SUCCESS, ClientStatusEnum.ASSISTANT_REVIEWED.getDescription()));
		case STAFF_REVIEWED:
			return ResponseResult
					.ok(HttpBody.build(MessageCode.SUCCESS, ClientStatusEnum.STAFF_REVIEWED.getDescription()));
		case MANAGER_REVIEWED:
			return ResponseResult
					.ok(HttpBody.build(MessageCode.SUCCESS, ClientStatusEnum.MANAGER_REVIEWED.getDescription()));
		case SUSPENDED:
			return ResponseResult.ok(HttpBody.build(MessageCode.SUCCESS, ClientStatusEnum.SUSPENDED.getDescription()));
		case BANNED:
			return ResponseResult.ok(HttpBody.build(MessageCode.SUCCESS, ClientStatusEnum.BANNED.getDescription()));
		default:
			return ResponseResult.failed(HttpBody.build(MessageCode.FAILED, null));
		}
	}

	/**
	 * 使用者會透過Email取得"進度查詢的驗證碼"
	 */
	@Override
	public ResponseEntity<Object> getRegistrationProgress(ClientDto clientDto) {
		String associatedApi = "registration_progress";
		Timestamp currentTime = Timestamp.from(Instant.now());
		Long emailExpirationTime = systemParameterSettingService.findEmailExpirationTime();

		if (clientDto == null) {
			return ResponseResult.validateArgsFailed(HttpBody.build(MessageCode.ACCOUNT_DOES_NOT_EXIST, null));
		}

		if (clientDto.getRegistrationProgressVerificationCode() != null
				&& currentTime.before(clientDto.getRegistrationProgressVerificationCodeExpiryTime())) {
			return ResponseResult.validateArgsFailed(HttpBody.build(MessageCode.LAST_VERIFICATION_CODE_VALID, null));

		}
		clientService.updateClientRegistrationProgressVerificationCodeExpiryTime(clientDto, emailExpirationTime);
		return this.sendVerificationEmail(clientDto.getEmail(), clientDto.getRegistrationProgressVerificationCode(),
				associatedApi) ? ResponseResult.ok(HttpBody.build(MessageCode.SUCCESS, null))
						: ResponseResult.failed(HttpBody.build(MessageCode.FAILED, "getRegistrationProgress is fail."));

	}

	@Override
	public ResponseEntity<Object> updateUserProfile(ClientDto clientDto, ClientInfoDto clientInfoDto) {
		clientInfoDto.setClientDto(clientDto);
		clientInfoService.updateUserProfile(clientInfoDto);
		return null;
	}

}
