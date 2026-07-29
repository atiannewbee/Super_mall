export function localDateInputValue(date = new Date()) {
  const pad = (value) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

export function validateProfile(profile, today = localDateInputValue()) {
  const errors = { nickname: '', phone: '', birthday: '' }
  const nickname = profile.nickname?.trim() || ''
  const phone = profile.phone?.trim() || ''

  if (!nickname) errors.nickname = '请输入昵称'
  else if (nickname.length > 50) errors.nickname = '昵称不能超过 50 个字符'

  if (phone && !/^1[3-9]\d{9}$/.test(phone)) errors.phone = '请输入有效的 11 位手机号'
  if (profile.birthday && profile.birthday > today) errors.birthday = '生日不能晚于今天'

  return errors
}
