<script setup>
import { ref } from 'vue'
import CommerceShell from '../components/CommerceShell.vue'

const active = ref('shipping')
const openQuestion = ref(0)
const topics = [
  { id: 'shipping', label: '配送服务', icon: '01' }, { id: 'payment', label: '支付与发票', icon: '02' },
  { id: 'returns', label: '退换与保修', icon: '03' }, { id: 'account', label: '账户与订单', icon: '04' },
]
const questions = {
  shipping: [
    ['如何查看物流进度？', '登录后进入“我的订单”，打开订单详情即可查看承运商、运单号和配送时间线。'],
    ['哪些地区支持次日达？', '当前页面使用深圳核心城区作为演示。正式时效将根据仓库库存、收货地址和物流接口实时计算。'],
    ['商品可以修改收货地址吗？', '未出库订单可联系客服申请修改；涉及敏感操作时需再次验证身份。'],
  ],
  payment: [
    ['支持哪些支付方式？', '当前支持支付宝与微信模拟支付，用于验证订单和库存流程，不会发生真实扣款；正式上线后再切换真实支付接口。'],
    ['支付成功但订单仍显示未支付怎么办？', '支付状态以后端回调为准。可以稍后刷新订单详情，仍未更新时请提供订单号联系客服。'],
    ['如何申请电子发票？', '结算时可以选择开票，也可以在订单完成后从订单详情补开。'],
  ],
  returns: [
    ['如何申请退货退款？', '在订单详情中选择“申请售后”，选择商品、类型和原因后提交即可跟踪进度。'],
    ['哪些商品支持 7 天无理由退货？', '未激活且包装、配件完好的商品通常支持。具体规则会依据商品品类和正式售后政策判断。'],
    ['商品出现质量问题怎么办？', '保留商品及包装，通过售后入口提交问题说明；后续版本支持上传图片和视频凭证。'],
  ],
  account: [
    ['在哪里查询自己的订单？', '登录后进入用户中心，选择“我的订单”，可以按待付款、待发货、待收货和已完成筛选。'],
    ['如何注册与登录？', '可以使用邮箱或中国大陆手机号注册。当前支持密码认证，短信验证码登录将在接入短信服务后开放。'],
    ['Agent 客服能直接退款吗？', '不能。未来 Agent 只能调用受控接口，高风险操作需要用户确认并记录审计日志。'],
  ],
}
</script>

<template>
  <CommerceShell>
    <main class="commerce-page help-page">
      <section class="help-hero"><div class="page-width"><p class="eyebrow eyebrow--light">SUPER SUPPORT</p><h1>需要帮助？<br /><i>答案</i>在这里。</h1><p>配送、支付、订单和售后常见问题。Agent 客服将在后续独立项目完成后接入。</p><div><RouterLink class="button button--light" to="/account/orders">查询我的订单</RouterLink><a href="#faq">浏览常见问题 ↓</a></div></div></section>
      <div id="faq" class="page-width help-content"><aside><p class="eyebrow">HELP TOPICS</p><button v-for="topic in topics" :key="topic.id" type="button" :class="{ 'is-active': active === topic.id }" @click="active = topic.id; openQuestion = 0"><span>{{ topic.icon }}</span>{{ topic.label }}<i>→</i></button></aside><section><header><p class="eyebrow">{{ topics.find(topic => topic.id === active)?.label }}</p><h2>常见问题</h2></header><article v-for="(question, index) in questions[active]" :key="question[0]" :class="{ 'is-open': openQuestion === index }"><button type="button" :aria-expanded="openQuestion === index" @click="openQuestion = openQuestion === index ? -1 : index"><span>0{{ index + 1 }}</span><b>{{ question[0] }}</b><i>{{ openQuestion === index ? '−' : '＋' }}</i></button><p v-if="openQuestion === index">{{ question[1] }}</p></article></section></div>
      <section id="promise" class="page-width help-promise"><article><span>01</span><h3>正品保障</h3><p>品牌授权渠道与可追溯商品信息。</p></article><article><span>02</span><h3>透明状态</h3><p>支付、履约、物流、售后分别记录。</p></article><article><span>03</span><h3>安全自助</h3><p>订单数据需要登录身份验证后查看。</p></article><article id="business"><span>04</span><h3>企业采购</h3><p>批量采购入口将在运营后台完成后开放。</p></article></section>
    </main>
  </CommerceShell>
</template>
