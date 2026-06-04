# Drama Poster Sources

Put the original poster images in this folder with these filenames, then run:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\build_drama_posters.ps1
```

Expected source filenames:

- `beipai_xunbao.jpg` - 北派寻宝
- `yunmiao.jpg` - 云渺
- `winter_solstice.jpg` - 那年冬至
- `beiwang.jpg` - 北往
- `diyi_wanku.jpg` - 天下第一纨绔
- `eighteen_grandma.jpg` - 十八岁太奶
- `lucky_divorce.jpg` - 幸得相遇离婚时
- `famine_village.jpg` - 荒年全村
- `home_inside_out.jpg` - 家里家外
- `siye.jpg` - 撕夜

`.png` and `.jpeg` are also accepted. The script trims white margins and writes generated assets into `frontend/assets/drama_posters/generated`.
