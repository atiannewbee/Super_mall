# 商家库存中心商品管理设计

## 目标

在现有商家“库存中心”内完成最小可用的商品新增、修改和删除，不新增页面、不区分仓库人员，也不修改数据库结构。

## 范围

- 库存列表继续以 SKU 为一行，并增加编辑、删除操作。
- 新增商品时同时创建一个默认 SKU、库存记录和封面图。
- 修改时更新商品基本信息、当前 SKU 和可售库存。
- 删除采用软删除，同时下线商品及其 SKU；历史订单保留快照，不受影响。
- 写接口仅允许商家 `OWNER` 或 `OPERATOR`，并始终按 `merchant_id` 校验数据归属。
- 每次增改删写入 `merchant_operation_logs`，库存变动写入 `inventory_transactions`。

## 最小字段

商品名、分类、封面图 URL、简介、上架状态、SKU 编码、规格名称、售价、原价和可售库存。Slug 由后端根据商品名和时间生成，避免前端承担唯一性处理。

## 接口

- `POST /api/merchant/products`：新增商品和默认 SKU。
- `PUT /api/merchant/products/{productId}/skus/{skuId}`：修改商品及当前 SKU。
- `DELETE /api/merchant/products/{productId}`：软删除商品。
- `GET /api/merchant/inventory`：沿用现有列表，并补充表单编辑所需字段。

## 验收

新增商品后能在库存中心出现；修改价格、库存和状态后能立即刷新；删除后不再出现在商家库存和消费者目录；跨商户数据不能修改；后端测试、商家前端测试和生产构建全部通过。
