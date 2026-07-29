const currency = new Intl.NumberFormat('zh-CN', {
  style: 'currency',
  currency: 'CNY',
  minimumFractionDigits: 2,
})

export function money(value) {
  return currency.format(Number(value || 0))
}

export function dateTime(value) {
  if (!value) return '—'
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(new Date(value))
}

export const statusLabels = {
  'pending-payment': '待支付',
  processing: '处理中',
  shipped: '已发货',
  completed: '已完成',
  cancelled: '已取消',
  unpaid: '未支付',
  paid: '已支付',
  closed: '已关闭',
  unfulfilled: '待拣货',
  picking: '拣货中',
  delivered: '已签收',
  returned: '已退回',
  'not-required': '无需履约',
  none: '无售后',
  requested: '售后申请中',
}

export function statusLabel(value) {
  return statusLabels[value] || value || '—'
}
