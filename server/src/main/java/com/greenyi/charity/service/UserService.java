package com.greenyi.charity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.greenyi.charity.common.BusinessException;
import com.greenyi.charity.entity.AppUser;
import com.greenyi.charity.entity.UserAddress;
import com.greenyi.charity.mapper.AppUserMapper;
import com.greenyi.charity.mapper.UserAddressMapper;
import com.greenyi.charity.security.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 用户与收货地址服务
 */
@Service
public class UserService {
    @Autowired private AppUserMapper userMapper;
    @Autowired private UserAddressMapper addressMapper;

    public AppUser getProfile(Long userId) {
        AppUser u = userMapper.selectById(userId);
        if (u == null) throw new BusinessException(404, "用户不存在");
        return u;
    }

    public void updateProfile(Long userId, Map<String, Object> body) {
        AppUser u = getProfile(userId);
        if (body.get("nickname") != null) u.setNickname(body.get("nickname").toString());
        if (body.get("avatarUrl") != null) u.setAvatarUrl(body.get("avatarUrl").toString());
        if (body.get("phone") != null) u.setPhone(body.get("phone").toString());
        if (body.get("guardianAuth") != null) u.setGuardianAuth(Integer.parseInt(body.get("guardianAuth").toString()));
        userMapper.updateById(u);
    }

    public List<UserAddress> listAddresses(Long userId) {
        return addressMapper.selectList(new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getUserId, userId).orderByDesc(UserAddress::getIsDefault));
    }

    public UserAddress saveAddress(Long userId, Map<String, Object> body) {
        UserAddress a = new UserAddress();
        Object id = body.get("id");
        if (id != null) {
            a.setId(Long.valueOf(id.toString()));
            UserAddress exist = addressMapper.selectById(a.getId());
            if (exist == null || !exist.getUserId().equals(userId)) throw new BusinessException(403, "无权修改该地址");
        }
        a.setUserId(userId);
        a.setReceiver(str(body.get("receiver")));
        a.setPhone(str(body.get("phone")));
        a.setProvince(str(body.get("province")));
        a.setCity(str(body.get("city")));
        a.setDistrict(str(body.get("district")));
        a.setDetail(str(body.get("detail")));
        a.setIsDefault(body.get("isDefault") == null ? 0 : Integer.parseInt(body.get("isDefault").toString()));
        if (!org.springframework.util.StringUtils.hasText(a.getReceiver()) || !org.springframework.util.StringUtils.hasText(a.getPhone())
                || !org.springframework.util.StringUtils.hasText(a.getDetail())) {
            throw new BusinessException(400, "收货人、电话、详细地址为必填");
        }
        if (a.getId() == null) addressMapper.insert(a); else addressMapper.updateById(a);
        if (a.getIsDefault() != null && a.getIsDefault() == 1) {
            addressMapper.selectList(new LambdaQueryWrapper<UserAddress>().eq(UserAddress::getUserId, userId))
                    .forEach(other -> { if (!other.getId().equals(a.getId()) && other.getIsDefault() == 1) {
                        other.setIsDefault(0); addressMapper.updateById(other); } });
        }
        return a;
    }

    public void deleteAddress(Long userId, Long addressId) {
        UserAddress a = addressMapper.selectById(addressId);
        if (a == null || !a.getUserId().equals(userId)) throw new BusinessException(403, "无权删除该地址");
        addressMapper.deleteById(addressId);
    }

    public UserAddress getAddress(Long userId, Long addressId) {
        UserAddress a = addressMapper.selectById(addressId);
        if (a == null || !a.getUserId().equals(userId)) throw new BusinessException(403, "地址不存在");
        return a;
    }

    private static String str(Object o) { return o == null ? null : o.toString(); }
}
