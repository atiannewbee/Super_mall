INSERT INTO categories (id, slug, name, icon, description, status, sort_order) VALUES
    (1, 'phones', '手机通讯', '01', '影像与移动性能', 'ACTIVE', 10),
    (2, 'computers', '电脑办公', '02', '轻薄本与桌面装备', 'ACTIVE', 20),
    (3, 'audio', '影音娱乐', '03', '沉浸聆听与创作', 'ACTIVE', 30),
    (4, 'smart-home', '智能家居', '04', '让空间主动响应', 'ACTIVE', 40),
    (5, 'accessories', '数码配件', '05', '精简但关键的装备', 'ACTIVE', 50);

INSERT INTO brands (id, code, name, status, sort_order) VALUES
    (1, 'AETHER', 'AETHER', 'ACTIVE', 10),
    (2, 'NOMAD', 'NOMAD', 'ACTIVE', 20),
    (3, 'PULSE', 'PULSE', 'ACTIVE', 30),
    (4, 'ORBIT', 'ORBIT', 'ACTIVE', 40),
    (5, 'VIEWEDGE', 'VIEWEDGE', 'ACTIVE', 50),
    (6, 'ARC', 'ARC', 'ACTIVE', 60),
    (7, 'KEYFRAME', 'KEYFRAME', 'ACTIVE', 70),
    (8, 'NESTA', 'NESTA', 'ACTIVE', 80);

INSERT INTO products (
    id, category_id, brand_id, slug, name, tagline, description, status,
    base_price, original_price, badge, accent_color, rating, review_count, sold_count,
    is_featured, is_new, is_deal, sort_order, published_at
) VALUES
    (101, 1, 1, 'aether-x1-pro', 'Aether X1 Pro', '把专业影像，装进口袋',
     '1 英寸主摄、全天候自适应屏幕与双向卫星通信，为移动创作者打造的旗舰手机。', 'ACTIVE',
     4299.00, 4799.00, '本周热卖', '#dfe8ff', 4.8, 2841, 12600, TRUE, TRUE, TRUE, 10, CURRENT_TIMESTAMP(3)),
    (102, 2, 2, 'nomadbook-air-14', 'NomadBook Air 14', '轻装上阵，灵感不掉线',
     '全金属机身仅重 1.08kg，配备高色域 OLED 屏与长续航架构，适合移动办公。', 'ACTIVE',
     5699.00, 6299.00, '新品', '#e7e2d8', 4.8, 1200, 5800, TRUE, TRUE, FALSE, 20, CURRENT_TIMESTAMP(3)),
    (103, 3, 3, 'pulse-studio-max', 'Pulse Studio Max', '安静下来，听见更多',
     '自适应空间音频与宽频主动降噪，让通勤、工作与长途旅行都保持专注。', 'ACTIVE',
     1599.00, 1899.00, '限时直降', '#fee2cf', 4.9, 3860, 18700, TRUE, FALSE, TRUE, 30, CURRENT_TIMESTAMP(3)),
    (104, 4, 4, 'orbit-watch-s', 'Orbit Watch S', '健康节奏，抬腕可见',
     '全天候健康趋势监测、专业运动模式与独立通信，兼顾轻量设计和户外可靠性。', 'ACTIVE',
     1299.00, 1499.00, '以旧换新', '#dce9e4', 4.8, 914, 4600, FALSE, TRUE, TRUE, 40, CURRENT_TIMESTAMP(3)),
    (105, 2, 5, 'viewedge-32', 'ViewEdge 32 设计显示器', '每一种颜色，都有依据',
     '32 英寸 4K 专业显示器，出厂逐台校色，并提供完整的桌面连接与供电能力。', 'ACTIVE',
     3299.00, 3699.00, '设计师推荐', '#dce6ec', 4.8, 643, 2300, FALSE, FALSE, TRUE, 50, CURRENT_TIMESTAMP(3)),
    (106, 3, 6, 'arcpods-pro', 'ArcPods Pro 2', '小体积，也有大声场',
     '半开放声学架构与智能降噪融合，提供舒适佩戴和清晰通话体验。', 'ACTIVE',
     699.00, 799.00, '人气单品', '#edf0f4', 4.8, 5240, 32100, FALSE, TRUE, FALSE, 60, CURRENT_TIMESTAMP(3)),
    (107, 5, 7, 'keyframe-75', 'Keyframe 75 键盘', '把每次输入，调到顺手',
     '75% 配列、热插拔轴座与三模连接，用克制的桌面体积提供完整的输入体验。', 'ACTIVE',
     499.00, 599.00, '桌搭精选', '#ece4db', 4.8, 1870, 9100, FALSE, FALSE, TRUE, 70, CURRENT_TIMESTAMP(3)),
    (108, 4, 8, 'homehub-mini', 'HomeHub Mini', '一句话，让家开始行动',
     '融合家庭中枢、智能音箱和环境感知，支持本地自动化与多协议设备接入。', 'ACTIVE',
     399.00, 499.00, '智能入门', '#e4e7dc', 4.8, 2240, 14800, FALSE, TRUE, FALSE, 80, CURRENT_TIMESTAMP(3));

INSERT INTO product_images (product_id, image_url, alt_text, image_type, sort_order) VALUES
    (101, 'https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?auto=format&fit=crop&w=1100&q=88', 'Aether X1 Pro', 'COVER', 0),
    (102, 'https://images.unsplash.com/photo-1496181133206-80ce9b88a853?auto=format&fit=crop&w=1100&q=88', 'NomadBook Air 14', 'COVER', 0),
    (103, 'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?auto=format&fit=crop&w=1100&q=88', 'Pulse Studio Max', 'COVER', 0),
    (104, 'https://images.unsplash.com/photo-1546868871-7041f2a55e12?auto=format&fit=crop&w=1100&q=88', 'Orbit Watch S', 'COVER', 0),
    (105, 'https://images.unsplash.com/photo-1527443224154-c4a3942d3acf?auto=format&fit=crop&w=1100&q=88', 'ViewEdge 32 设计显示器', 'COVER', 0),
    (106, 'https://images.unsplash.com/photo-1590658268037-6bf12165a8df?auto=format&fit=crop&w=1100&q=88', 'ArcPods Pro 2', 'COVER', 0),
    (107, 'https://images.unsplash.com/photo-1587829741301-dc798b83add3?auto=format&fit=crop&w=1100&q=88', 'Keyframe 75 键盘', 'COVER', 0),
    (108, 'https://images.unsplash.com/photo-1589492477829-5e65395b66cc?auto=format&fit=crop&w=1100&q=88', 'HomeHub Mini', 'COVER', 0);

INSERT INTO product_features (product_id, content, sort_order) VALUES
    (101, '1 英寸影像传感器', 10), (101, '2K 自适应高刷屏', 20), (101, '100W 闪充', 30),
    (102, '2.8K OLED 屏', 10), (102, '18 小时续航', 20), (102, '1.08kg 机身', 30),
    (103, '-48dB 主动降噪', 10), (103, '60 小时续航', 20), (103, '无损空间音频', 30),
    (104, '双频 GPS', 10), (104, '14 天续航', 20), (104, '全天健康趋势', 30),
    (105, '4K IPS Black', 10), (105, '98% P3 色域', 20), (105, '90W USB-C', 30),
    (106, '智能动态降噪', 10), (106, '36 小时续航', 20), (106, 'IP55 防护', 30),
    (107, '全键热插拔', 10), (107, '三模连接', 20), (107, 'Gasket 结构', 30),
    (108, 'Matter 多协议', 10), (108, '本地场景自动化', 20), (108, '360° 远场语音', 30);

INSERT INTO product_attributes (id, product_id, name, sort_order) VALUES
    (2001, 101, '颜色', 10), (2002, 101, '存储容量', 20),
    (2003, 102, '颜色', 10), (2004, 102, '内存', 20), (2005, 102, '存储容量', 30),
    (2006, 103, '颜色', 10),
    (2007, 104, '表径', 10), (2008, 104, '表带', 20),
    (2009, 105, '支架', 10),
    (2010, 106, '颜色', 10),
    (2011, 107, '颜色', 10), (2012, 107, '轴体', 20),
    (2013, 108, '颜色', 10);

INSERT INTO product_attribute_values (id, attribute_id, value, sort_order) VALUES
    (3001, 2001, '曜石黑', 10), (3002, 2001, '月岩银', 20),
    (3003, 2002, '256GB', 10), (3004, 2002, '512GB', 20),
    (3005, 2003, '雾银', 10), (3006, 2003, '深空灰', 20),
    (3007, 2004, '16GB', 10), (3008, 2004, '32GB', 20),
    (3009, 2005, '512GB', 10), (3010, 2005, '1TB', 20),
    (3011, 2006, '曜石黑', 10), (3012, 2006, '沙砾金', 20),
    (3013, 2007, '42mm', 10), (3014, 2007, '46mm', 20),
    (3015, 2008, '黑色硅胶', 10), (3016, 2008, '钛灰钢带', 20),
    (3017, 2009, '标准升降支架', 10), (3018, 2009, '悬浮显示器臂', 20),
    (3019, 2010, '陶瓷白', 10), (3020, 2010, '雾海蓝', 20),
    (3021, 2011, '云杉绿', 10), (3022, 2011, '岩灰', 20),
    (3023, 2012, '轻触轴', 10), (3024, 2012, '线性轴', 20),
    (3025, 2013, '云朵白', 10), (3026, 2013, '苔原绿', 20);

INSERT INTO product_skus (id, product_id, sku_code, label, price, original_price, status, sort_order) VALUES
    (1001, 101, 'aether-x1-256-black', '曜石黑 / 256GB', 4299.00, 4799.00, 'ACTIVE', 10),
    (1002, 101, 'aether-x1-512-silver', '月岩银 / 512GB', 4899.00, NULL, 'ACTIVE', 20),
    (1003, 102, 'nomad-air-16-512', '雾银 / 16GB + 512GB', 5699.00, 6299.00, 'ACTIVE', 10),
    (1004, 102, 'nomad-air-32-1t', '深空灰 / 32GB + 1TB', 7299.00, NULL, 'ACTIVE', 20),
    (1005, 103, 'pulse-max-black', '曜石黑', 1599.00, 1899.00, 'ACTIVE', 10),
    (1006, 103, 'pulse-max-sand', '沙砾金', 1699.00, NULL, 'ACTIVE', 20),
    (1007, 104, 'orbit-s-42-black', '42mm / 黑色硅胶', 1299.00, 1499.00, 'ACTIVE', 10),
    (1008, 104, 'orbit-s-46-steel', '46mm / 钛灰钢带', 1699.00, NULL, 'ACTIVE', 20),
    (1009, 105, 'viewedge-32-stand', '标准升降支架', 3299.00, 3699.00, 'ACTIVE', 10),
    (1010, 105, 'viewedge-32-arm', '悬浮显示器臂', 3599.00, NULL, 'ACTIVE', 20),
    (1011, 106, 'arcpods-pro-white', '陶瓷白', 699.00, 799.00, 'ACTIVE', 10),
    (1012, 106, 'arcpods-pro-blue', '雾海蓝', 699.00, NULL, 'ACTIVE', 20),
    (1013, 107, 'keyframe-75-brown', '云杉绿 / 轻触轴', 499.00, 599.00, 'ACTIVE', 10),
    (1014, 107, 'keyframe-75-linear', '岩灰 / 线性轴', 529.00, NULL, 'ACTIVE', 20),
    (1015, 108, 'homehub-mini-white', '云朵白', 399.00, 499.00, 'ACTIVE', 10),
    (1016, 108, 'homehub-mini-green', '苔原绿', 399.00, NULL, 'ACTIVE', 20);

INSERT INTO sku_attribute_values (sku_id, attribute_value_id) VALUES
    (1001, 3001), (1001, 3003), (1002, 3002), (1002, 3004),
    (1003, 3005), (1003, 3007), (1003, 3009),
    (1004, 3006), (1004, 3008), (1004, 3010),
    (1005, 3011), (1006, 3012),
    (1007, 3013), (1007, 3015), (1008, 3014), (1008, 3016),
    (1009, 3017), (1010, 3018),
    (1011, 3019), (1012, 3020),
    (1013, 3021), (1013, 3023), (1014, 3022), (1014, 3024),
    (1015, 3025), (1016, 3026);

INSERT INTO sku_inventory (sku_id, available_quantity, locked_quantity, sold_quantity) VALUES
    (1001, 18, 0, 0), (1002, 9, 0, 0),
    (1003, 12, 0, 0), (1004, 6, 0, 0),
    (1005, 32, 0, 0), (1006, 14, 0, 0),
    (1007, 20, 0, 0), (1008, 8, 0, 0),
    (1009, 10, 0, 0), (1010, 5, 0, 0),
    (1011, 48, 0, 0), (1012, 0, 0, 0),
    (1013, 16, 0, 0), (1014, 11, 0, 0),
    (1015, 35, 0, 0), (1016, 22, 0, 0);

INSERT INTO inventory_transactions (
    sku_id, transaction_type, available_delta, locked_delta, reference_type, reference_no, note
)
SELECT sku_id, 'IN', available_quantity, 0, 'CATALOG_SEED', 'V2', '前端目录初始库存'
FROM sku_inventory
WHERE available_quantity > 0;
