# 自定义包实现
* 后续通过CodecProcessor 实现自定义包的注册与更新，减少Protocol项目的修改
* 切记更新runtimeID，切记添加网易额外新增的mod类物品，比如：minecraft:mod_ore,minecraft:micro_block
* 注意JavaSectionBlocksUpdateTranslator是否使用UpdateSubChunkBlocksPacket处理过大的包，老版本会将每个方块都发一次updateblock包。
* 在后续更新版本时，可视情况移除SkullBlockEntityTranslator
* 网易针对物品的Enchant有新增字段GeyserEnchantOption#
* 注意网易的AuthInput包，在CodecProcessor中的网易专用 PlayerAuthInput 处理逻辑
* 检查是否实现PC/PE皮肤是否正确显示
* 检查是否实现自定义头颅方块
* 检查是否实现自定义实体功能
* 检查是否实现行为包加载
* 检查是否实现自定义资源包功能
* 检查是否实现实现通过网易代理获取玩家真实IP
* 检查MAPPING，检查是否添加网易的mod_xx 系列物品/方块
* 注意java药水持续时间为-1代表无限，但PE不认，需处理。JavaUpdateMobEffectTranslator，最大值为
* 注意更新区块范围需限制最大10格，因为低版本viaversion会强制为96导致低端设备严重卡顿或崩溃。
