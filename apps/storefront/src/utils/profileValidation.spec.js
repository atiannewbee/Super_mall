import { describe, expect, it } from 'vitest'
import { localDateInputValue, validateProfile } from './profileValidation'

describe('validateProfile', () => {
  it('formats the browser-local date for date inputs', () => {
    expect(localDateInputValue(new Date(2026, 6, 23, 0, 5))).toBe('2026-07-23')
  })

  it('accepts a valid optional phone and birthday', () => {
    expect(validateProfile(
      { nickname: '小超', phone: '13800138000', birthday: '2000-01-01' },
      '2026-07-23',
    )).toEqual({ nickname: '', phone: '', birthday: '' })
  })

  it('rejects empty names, malformed phones and future birthdays', () => {
    expect(validateProfile(
      { nickname: '  ', phone: '123', birthday: '2027-01-01' },
      '2026-07-23',
    )).toEqual({
      nickname: '请输入昵称',
      phone: '请输入有效的 11 位手机号',
      birthday: '生日不能晚于今天',
    })
  })
})
