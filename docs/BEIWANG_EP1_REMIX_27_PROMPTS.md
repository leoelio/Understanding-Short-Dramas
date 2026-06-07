# 北往第一集片尾 AI 二创 27 图文案与图片提示词

## 设计策略

片尾二创不做“实时无限生成”，第一版采用“主分支 + 个性化小选择 + 预生成缓存”的方式。

- 主分支 1：车坏了，但继续骑回家。
- 主分支 2：太苦了，改用公共交通回家。
- 主分支 3：帮了别人，车没了，但被对方带回家。
- 每个主分支给 3 个个性化选项。
- 每个选项生成 3 张分镜图。
- 合计 3 * 3 * 3 = 27 张图。

核心要求：用户选择的差异必须进入画面。饮料、票种、车辆类型都要在图中明显出现。车辆品牌采用“双层表达”：结果卡 UI 明确显示品牌名，图片中体现对应车型轮廓、车标位置和品牌感前脸，但不强制生成可读商标文字和可读车牌，避免图像模型文字失真和后续上线授权风险。

## 通用图片生成基础提示词

所有图片生成时都应拼接以下基础提示词，再追加每张图的专属提示词。

## 参考图输入包

当前已有参考图可以支撑第一轮生成，不需要额外截图：

- `data/remix_image_refs/beiwang_ep1/motorcycle_departure_reference.png`：可选但强烈推荐。两位主角骑各自摩托从第一集结尾出发的同框图，优先用于锁定两位主角、两辆旧摩托、行李位置、道路纵深和“继续往北走”的出发情绪。
- `data/remix_image_refs/beiwang_ep1/pi_desheng_blue_overshirt.png`：男主 A 近景，确认发型、脸型、蓝灰外套、红色内搭。
- `data/remix_image_refs/beiwang_ep1/pi_desheng_scene_body.png`：男主 A 场景半身，确认身形、衣服比例和原剧自然光质感。
- `data/remix_image_refs/beiwang_ep1/li_shixin_plaid_overshirt.png`：男主 B 近景，确认短发、格子衬衫、灰色内搭。
- `data/remix_image_refs/beiwang_ep1/li_shixin_scene_body.png`：男主 B 场景半身，确认同框比例和原剧街景质感。
- `data/remix_image_refs/beiwang_ep1/*_scene_*.png`：原剧道路、车站、骑行和室外画风参考。

生成时只把这些图当作“人物和画风参考”，不能复制其中的字幕、招牌、路牌、车牌或画面文字。如果后续某张图人物偏差仍然明显，再补两类截图：无遮挡正脸近景、同框全身/半身场景图。

```text
Edit the uploaded episode stills into one vertical 9:16 cinematic still for a Chinese short drama extension.
Use the uploaded stills as character references for the two correct male leads from Beiwang episode 1.
Uploaded reference stills may contain subtitles, storefront signs, road signs, or frame text; ignore all text in the references and do not reproduce it.
Story continuity begins from the episode ending: both male leads riding their own old motorcycles on an open road. If a two-rider motorcycle departure still is uploaded, treat it as the highest-priority reference for the two leads, the two motorcycles, luggage positions, road depth, and departure mood. Only show the motorcycles in shots where the story logically still includes them.
Primary lead A: young Chinese man with messy black hair, blue-gray overshirt, red inner shirt, tired but stubborn expression.
Primary lead B: young Chinese man with short black hair, beige plaid/checkered overshirt and gray tee, practical and anxious expression.
Both male leads must remain visually consistent with the episode references.
Use grounded Chinese short-drama realism, mobile vertical composition, cinematic natural light, realistic winter road atmosphere.
Reserve a clean lower-third safe area for app subtitle and voice-caption overlay.
Do not draw subtitles, readable text, watermarks, UI, road-sign words, license plates, or captions inside the image. For branch 3 vehicle scenes, a non-readable brand-like grille badge or emblem position is allowed when explicitly requested, but license plates must remain blank or unreadable.
Any cans, bottles, tickets, phones, vehicle badges, signs, labels, plates, and route boards must be blank or unreadable.
The scene must feel like a plausible continuation after the motorcycle reveal in Beiwang episode 1.
```

## 分支一：车坏了，但继续骑回家

用户看到的问题：路上摩托坏了，冷风里又渴又累，他会买什么撑住继续走？

用户选项：

- 红罐可乐：偏轻喜剧和提神，“喝口甜的，继续硬扛”。
- 绿罐汽水：偏荒诞爽感，“越苦越得来点冲的”。
- 矿泉水：偏现实克制，“不矫情，补口水继续赶路”。

### 1A 红罐可乐

展示标题：红罐可乐救场

剧情文案：

摩托后轮在荒路上扎进铁钉，车身一沉，他差点把整个人的劲儿都泄掉。路边小卖部已经要关门，他用最后几枚零钱买了一罐红色可乐，冰得手心发麻。老板娘翻出旧补胎工具，他灌下一口甜的，像把那点委屈也咽了回去。补好胎后，他把空罐塞进包侧袋，重新扶起摩托：甜是甜，但家还远。

分镜 1：荒路爆胎

台词：还有几十公里，不能在这儿停。

图片提示词：

```text
A realistic vertical cinematic still: on a bleak Northeast China winter roadside near an abandoned toll booth, the old motorcycle has a flat rear tire with a small nail visible in the rubber. Lead A in a blue-gray overshirt crouches beside the tire, lead B in a beige plaid overshirt leans over anxiously. Luggage is tied to the motorcycle. Cold wind, gray sky, empty road, grounded drama tension. No text, no logos, no readable license plate.
```

分镜 2：买红罐可乐

台词：喝口甜的，手就不抖了。

图片提示词：

```text
A warm-lit rural convenience store half-closing at night. Lead A stands by the counter holding a plain red soda can with no logo or readable label; his fingers are red from cold. Lead B holds old tire repair tools. The shopkeeper's hand passes a cup of hot water from inside the shop. The red can must be clearly visible as the user's chosen item. Warm interior light contrasts with cold blue street outside. No readable text, no brand logo.
```

分镜 3：补好继续走

台词：甜是甜，路还得自己骑。

图片提示词：

```text
The motorcycle tire is repaired under a dim roadside lamp. Lead A pushes the motorcycle upright with a tired half-smile, a plain red soda can tucked visibly in the side pocket of his bag. Lead B tightens the luggage rope. The road stretches into winter dusk, hopeful but realistic. Vertical mobile drama composition, lower third left clean for subtitles. No text, no logos.
```

### 1B 绿罐汽水

展示标题：绿罐汽水提神

剧情文案：

铁钉扎破后轮时，他第一反应不是疼，是烦。小卖部老板说补胎工具旧得不一定好使，他却从冰柜里拿出一罐绿色汽水，说越冷越得来点冲的。汽水开罐的一声像给自己壮胆，气泡顶上来，他也跟着缓过来。补胎时手冻得直抖，嘴上还贫：这玩意儿比我摩托还带劲。等后轮重新鼓起来，他把易拉罐压扁，继续往北骑。

分镜 1：后轮泄气

台词：不是，连轮胎都想过年歇着？

图片提示词：

```text
A grounded short-drama still on a lonely winter road. The old motorcycle leans slightly because the rear tire is flat. Lead A points at the tire in disbelief with a bitter comic expression; lead B squats to inspect the nail. Luggage bundle on the back seat. The scene should feel frustrating but slightly humorous. No text, no logos.
```

分镜 2：买绿罐汽水

台词：越苦，越得来点冲的。

图片提示词：

```text
Inside the doorway of a small rural shop at night, Lead A opens a plain green lemon-lime soda can with no logo, visible fizz rising at the mouth of the can. Lead B carries a worn tire repair kit and looks amused. Cold outdoor air meets warm shop light. The green can must be central and clearly visible, but no readable label or brand mark. Realistic Chinese short-drama style.
```

分镜 3：气泡和发动机

台词：行，咱俩都别泄气。

图片提示词：

```text
Lead A kneels beside the repaired rear tire, holding the plain green soda can near the motorcycle seat, joking with the old motorcycle as if encouraging it. Lead B tightens the valve cap. White breath mist, winter roadside, soft headlight glow. The green can remains visible as the chosen prop. No text, no logos, no license plate.
```

### 1C 矿泉水

展示标题：矿泉水硬扛

剧情文案：

后轮扎钉后，他站在路边沉默了很久。小卖部的冰柜里有饮料，他最后只拿了一瓶没有商标的矿泉水。不是不想喝甜的，是怕钱不够。老板娘看懂了，没多说，把旧补胎工具推给他。矿泉水灌下去又凉又苦，他却清醒了些。补胎、绑绳、推车，一样一样做完，他对工友说：别担心，我不乱花钱，也不放弃。

分镜 1：荒路停住

台词：省一点，才能多走一点。

图片提示词：

```text
Lead A stands beside the old motorcycle with a flat tire on a barren winter roadside, counting a few coins in his palm. Lead B checks the tire and luggage. The mood is restrained and realistic, less comedic, more survival and dignity. Empty rural road, cold gray-blue color tone. No readable text, no logos.
```

分镜 2：买矿泉水

台词：不用甜的，能走就行。

图片提示词：

```text
At a small rural shop entrance, Lead A holds a clear mineral water bottle with a completely blank label, no logo and no readable text. He looks tired but composed. Lead B holds the repair tools. The shopkeeper silently places the tools on the counter. The clear water bottle must be clearly visible as the user's chosen item. Warm shop light, cold winter outside.
```

分镜 3：冷水醒神

台词：别担心，我还往回走。

图片提示词：

```text
Lead A drinks from a clear unlabeled water bottle beside the repaired motorcycle, then reaches to start the bike. Lead B secures the luggage rope. The image should feel quiet, practical, and emotionally grounded. A long road behind them, winter dusk, no readable signs, no logo, clean lower third.
```

## 分支二：太苦了，改用公共交通回家

用户看到的问题：骑车太苦，他终于想改交通方式。买什么票回家？

用户选项：

- 绿皮火车票：最有年代感和返乡感。
- 长途大巴票：更现实、更狼狈，但也更快。
- 高铁站票：更现代、更拥挤，突出“哪怕站着也要回去”。

### 2A 绿皮火车票

展示标题：绿皮火车票

剧情文案：

连续几个小时的寒风钻进棉衣，他第一次承认自己可能扛不住了。候车亭里，一个小女孩抱着冻硬的饺子盒问爸爸：奶奶会等我们吗？那句话比风还扎人。他给工友发去语音：别的不用，先借我一张票钱。夜里，他把摩托寄存在小站，攥着一张没有文字的旧式纸票挤上绿皮火车。车不骑了，家还得回。

分镜 1：候车亭动摇

台词：我是不是扛不住了？

图片提示词：

```text
A cold rural bus stop or small station shelter at night. Lead A sits with his helmet beside him, exhausted, old motorcycle parked nearby with luggage. Lead B stands outside the shelter watching the road. Cold wind bends the plastic curtain. The mood is hesitation and fatigue. No readable text or route signs.
```

分镜 2：借钱买票

台词：哥，先借我一张票钱。

图片提示词：

```text
Lead A holds a phone close to his mouth recording a voice message to borrow ticket money. In his other hand is a blank old-style paper train ticket with no readable text. Lead B watches quietly. The background is a small-town train station corner with warm yellow lights and blurred waiting passengers. No readable signs, no logos.
```

分镜 3：绿皮火车启动

台词：车不骑了，家还得回。

图片提示词：

```text
A green older-style train slowly departing a small snowy station at night. Lead A stands near the carriage connection holding luggage, looking out through the open doorway or window; Lead B stands behind him. The old motorcycle is faintly visible parked outside the station in the background. The blank paper ticket is visible in Lead A's hand. No readable text, no station name, no logos.
```

### 2B 长途大巴票

展示标题：最后一班大巴

剧情文案：

服务区的热水机坏了，他坐在台阶上，手指冻得握不住钥匙。母亲发来语音：别逞能，平安到就行。他听完沉默了很久，给工友打电话，声音像认输：哥，借我点票钱。最后一班大巴只剩一张后排票，他把摩托托管在服务区，抱着行李上车。大巴尾灯亮起来时，他第一次允许自己不硬撑。

分镜 1：服务区崩溃

台词：再骑下去，真回不去了。

图片提示词：

```text
Winter night at a roadside service area. Lead A sits on concrete steps, unable to hold the motorcycle key properly because his fingers are frozen. Lead B stands beside the old motorcycle, concerned. A broken hot water machine is visible but any label or notice must be blank and unreadable. Empty service area atmosphere, realistic drama.
```

分镜 2：借钱买大巴票

台词：哥，借我点票钱。

图片提示词：

```text
Lead A stands under a cold service-area light, listening to his mother's voice message on the phone, then calling a coworker. A blank long-distance coach ticket with no readable text is visible in his hand. Lead B holds luggage, waiting silently. The old motorcycle is parked nearby. Realistic, restrained emotion.
```

分镜 3：坐上后排

台词：不逞强，也算往家走。

图片提示词：

```text
Inside the last long-distance coach bus at night, Lead A sits in the back row holding luggage tightly, Lead B sits beside him or across the aisle. Warm bus interior light, fogged window, service area lights and the parked motorcycle fading outside. The blank bus ticket is tucked into his coat pocket. No readable text, no logos.
```

### 2C 高铁站票

展示标题：高铁站票

剧情文案：

暴雪预警一响，路口把摩托全拦下。主角看着手机上跳出的封路提示，嘴硬了半天，最后给工友发消息：高铁站票也行，帮我抢一张。高铁站里人潮往检票口挤，他背着行李站在候车大厅边上，鞋边还沾着雪泥。有人说站几个小时太遭罪，他却笑了一下：站着也行，站着说明我还在往家赶。

分镜 1：暴雪封路

台词：前面封了，不能骑。

图片提示词：

```text
A snowy road checkpoint in Northeast China. Lead A and Lead B stand beside the old motorcycle as road staff stop motorcycles from continuing. Warning lights glow through heavy snow, but no readable words or signs. The scene should feel dangerous and forced to stop. Vertical cinematic realism.
```

分镜 2：抢高铁站票

台词：站着也行，能回去就行。

图片提示词：

```text
Crowded modern high-speed railway station corner. Lead A stares at his phone and confirms a last-minute high-speed rail standing ticket, but the phone screen must be blank or unreadable. Lead B holds the luggage and looks relieved. A blank ticket stub is visible between Lead A's fingers. Sleek station architecture, moving crowd blur, no readable station signs, no logos.
```

分镜 3：挤上车厢

台词：站着也算往家赶。

图片提示词：

```text
Inside a crowded modern high-speed train vestibule or aisle at night. Lead A stands with luggage strapped to his shoulder, shoes wet with melting snow; Lead B braces himself nearby. Clean modern train interior, warm carriage light, crowded but not chaotic, human warmth. The blank high-speed rail standing ticket is partly visible in Lead A's hand. No readable text, no route board, no logos.
```

## 分支三：帮了别人，车没了，但被对方带回家

用户看到的问题：他停下来帮别人，自己的摩托却没了。帮到的是什么车？

用户选项：

- 五菱面包车：最接地气，偏温暖现实；UI 显示“五菱”，图像体现白色面包车和红色五菱风格车标位置。
- 奥迪轿车：偏命运反差，善意换善意；UI 显示“奥迪”，图像体现黑色豪华轿车和四环风格车标位置。
- 揽胜越野：偏强视觉和安全感，更适合雪路救援；UI 显示“揽胜”，图像体现深色豪华越野车、矩形前脸和车标位置。

注意：比赛展示版允许图片里出现品牌感车标位置，但不依赖可读 logo。正式上线前建议把真实品牌替换为虚构品牌或取得授权。

### 3A 五菱面包车

展示标题：五菱面包车顺路

剧情文案：

路边一辆白色面包车陷进雪沟，车主急得满头汗，后座还坐着抱保温桶的母亲。主角二话没说下车帮推，鞋全湿了。等车出来，他回头一看，自己的摩托不见了，只剩地上一道拖痕。他愣住时，车主听见他的东北口音，拍了拍副驾：兄弟，上车吧，我也往北走。摩托丢了，回家的路却多了个人情味。

分镜 1：帮推面包车

台词：搭把手，先把人弄出来。

图片提示词：

```text
Snowy roadside at night. A plain white Wuling Hongguang-style minivan is stuck in a snow ditch; the front grille should include a small red Wuling-like badge shape so the brand choice is visually clear, but no readable text and no readable license plate. Lead A and Lead B push from behind, shoes wet in snow. The driver anxiously steers, an elderly mother holding a thermos sits in the back. Warm human realism.
```

分镜 2：摩托不见

台词：我车呢？

图片提示词：

```text
After the minivan is freed, Lead A turns back and finds the old motorcycle missing. Only drag marks in the snow and a fallen rope remain. Lead B freezes beside him, shocked. The plain white Wuling Hongguang-style minivan is visible nearby, headlights on, with a small red Wuling-like badge shape on the grille, no readable text, no readable plate. Short-drama reversal, realistic snow night.
```

分镜 3：顺路向北

台词：上车，都是往东北回。

图片提示词：

```text
The plain white Wuling Hongguang-style minivan passenger door opens, warm interior light spilling onto Lead A's tired face. Lead B loads luggage into the minivan. The driver gestures toward the empty passenger seat, inviting them in. Snowy road ahead, warm realistic emotion. The vehicle must look like a practical white minivan, with a small red brand-like badge position visible, no readable text, no readable plate.
```

### 3B 奥迪轿车

展示标题：奥迪轿车顺路

剧情文案：

一辆黑色奥迪轿车停在路肩，车主急着送老人去前面镇医院，却不会换备胎。主角本来赶时间，还是把摩托停下，脱了手套帮他拧螺丝。老人上车前塞给他一把热糖。等他回到路边，摩托已经被人推走。车主沉默几秒，把后备箱重新打开：兄弟，你帮我一程，我送你一程。雪夜里，副驾的安全带替他接住了那口委屈。

分镜 1：帮换备胎

台词：先救急，别耽误老人。

图片提示词：

```text
A dark black Audi-style luxury sedan is parked on a snowy roadside shoulder. The front grille should show a four-ring-like emblem position or subtle chrome ring badge shape, recognizable as Audi-style, but no readable text and no readable license plate. Lead A crouches to change a spare tire with a wrench, Lead B holds a flashlight. An elderly person wrapped in a scarf waits inside the car. Premium sedan shape and lighting.
```

分镜 2：热糖和失车

台词：这也太背了吧。

图片提示词：

```text
The elderly passenger gives Lead A a few wrapped warm candies, but Lead A turns back and sees his old motorcycle missing from the roadside. Lead B points at drag marks in the snow. The dark Audi-style luxury sedan remains nearby with trunk open; four-ring-like grille badge shape is visible but no readable text, no readable plate. Emotional reversal, realistic winter night. Candy wrappers blank.
```

分镜 3：副驾回家

台词：你帮我一程，我送你一程。

图片提示词：

```text
Lead A places his worn luggage into the trunk of the dark Audi-style luxury sedan; Lead B sits in the back seat, still holding the helmet. The driver opens the passenger door for Lead A. A subtle four-ring-like emblem position is visible on the grille or steering wheel, but no readable text, no readable license plate, no dashboard text. Warm interior light, snowy road outside, quiet dignity.
```

### 3C 揽胜越野

展示标题：越野车雪路救援

剧情文案：

雪越下越厚，一辆深色揽胜越野陷在村外坡道，车主急着把药送回家。主角看了眼时间，还是停下帮他垫木板、推车。车轮终于咬住雪面冲出来，他却发现自己的摩托被人顺走了。车主没说漂亮话，只把后排座椅放倒，腾出一块位置：东西放上来，我车能走雪路，先送你回东北。那一刻，他第一次觉得这条路没那么孤单。

分镜 1：越野车陷坡

台词：雪路难走，先帮他出来。

图片提示词：

```text
A dark Range Rover-style luxury SUV is stuck on a snowy village slope at night. Large wheels, boxy premium silhouette, rectangular grille, and a non-readable Range-Rover-like hood badge area should be visible, but no readable lettering and no readable license plate. Lead A and Lead B place wooden planks under the tires and push. The driver holds a small medicine bag anxiously. Snowfall, strong headlight beams, realistic rescue tension.
```

分镜 2：摩托被顺走

台词：我摩托呢？

图片提示词：

```text
The dark Range Rover-style SUV has just escaped the snowy slope. Lead A looks back in shock: the old motorcycle is gone, leaving tire tracks and a loose luggage rope in the snow. Lead B scans the road, worried. The SUV headlights light the empty spot. The vehicle has a clear rectangular luxury SUV front and non-readable hood badge area, no readable plate. Strong cinematic contrast.
```

分镜 3：越野车送回家

台词：东西放上来，我送你往北走。

图片提示词：

```text
The dark Range Rover-style SUV rear door is open, rear seats folded down for luggage. Lead A and Lead B load their worn bags inside. The driver gestures firmly, offering a ride through the snowy road. The vehicle should show a premium boxy SUV profile and a non-readable brand badge area, no readable license plate, no text. Powerful but warm rescue feeling, winter night, headlight glow.
```

## 第一版推荐默认生成顺序

为了比赛展示，第一批先生成全部 27 张，但前端第一屏默认展示每个主分支的第一张封面：

1. 红罐可乐救场 - 荒路爆胎。
2. 绿皮火车票 - 候车亭动摇。
3. 五菱面包车顺路 - 帮推面包车。

用户进入某个分支后，再选择该分支内的个性化选项。

## 前端交互建议

片尾入口：

- 标题：如果这趟北往不止一种走法？
- 副标题：选一个细节，让 AI 给你展开另一段回家路。

第一层选择：

- 车坏了怎么办？
- 太苦了还骑吗？
- 帮人后车没了？

第二层选择：

- 分支一：红罐可乐 / 绿罐汽水 / 矿泉水。
- 分支二：绿皮火车 / 长途大巴 / 高铁站票。
- 分支三：五菱面包车 / 奥迪轿车 / 揽胜越野。

结果页：

- 点击图片进入下一张。
- 每张图底部显示一句台词。
- 语音可切换：原版旁白 / 我的声音带入版。
- 允许保存为“我的 AI 剧情卡”，后续可分享到逛逛。
