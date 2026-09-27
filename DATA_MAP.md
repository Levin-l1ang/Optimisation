# LinTim 数据地图与复用约定

## 读取规则

以 `data/openlintim-master` 为 ROOT；所有 relative_path 均相对此目录。
`results/tables/file_manifest.csv` / `results/audit/file_manifest.json` 为完整文件索引，不能仅用文件名定位。
`results/audit/directory_tree.txt` 列出所有目录及文件；不限制递归深度。
`results/tables/view_registry.csv` 为本次实际读取路径注册表，下面是其站点/边部分。
`results/tables/effective_config.csv` 记录最终参数、来源文件与行号；配置 include 按顺序覆盖。
SHA-256 用于确认后续打开的文件仍是本次快照。

`datasets/<dataset>/basis/` 保存基础数据；`line-planning/` 保存线路方案；其他规划阶段目录存在不代表已有结果。
`src/` 为算法实现，`ci/` 为测试夹具，不参与主体 EDA。

## 防止误解

- 分号分隔、# 注释、无正式表头；保留 _source_line。
- stop_id、edge_id、line_id 都只在所属 dataset/view 内连接。模式间相同站点 ID 依手册视为同一基础设施节点，但须核查坐标。
- joint 是组合视图，不同模式分段客流不可简单相加当作总乘客。
- lowersaxony 使用 Existing-Stop/Existing-Edge，它不是已完成选址的最终 PTN。
- dataset-generation 与 template 没有可分析网络；配置预期文件缺失不自动算质量错误。
- 没有时刻表、延误观测或日期字段；仅 BOMHarbour 有 Line-Concept.lin。
- Edge.length × gen_conversion_length 得 km；x/y 不是默认经纬度。路段时间分析保留 raw 单位；Athens time_units_per_minute=10。
- shortest paths 保留方向；平行同向边取最小 lower bound，原始多重边记录未删。
- 数据可能经过合成、聚合或模型计算。Stored Load 与 OD 的关系不等于独立真实观测。

## 每个网络的准确输入路径

|view|directed|stop_path|edge_path|
|---|---|---|---|
|BOMHarbour/default|False|`datasets/BOMHarbour/basis/Stop.giv`|`datasets/BOMHarbour/basis/Edge.giv`|
|athens/default|False|`datasets/athens/basis/Stop.giv`|`datasets/athens/basis/Edge.giv`|
|goevb/default|True|`datasets/goevb/basis/Stop.giv`|`datasets/goevb/basis/Edge.giv`|
|grid/default|False|`datasets/grid/basis/Stop.giv`|`datasets/grid/basis/Edge.giv`|
|grid-multimodal/bike|False|`datasets/grid-multimodal/basis/bike.Stop.giv`|`datasets/grid-multimodal/basis/bike.Edge.giv`|
|grid-multimodal/bus|False|`datasets/grid-multimodal/basis/bus.Stop.giv`|`datasets/grid-multimodal/basis/bus.Edge.giv`|
|grid-multimodal/joint|False|`datasets/grid-multimodal/basis/joint.Stop.giv`|`datasets/grid-multimodal/basis/joint.Edge.giv`|
|grid-multimodal/tram|False|`datasets/grid-multimodal/basis/tram.Stop.giv`|`datasets/grid-multimodal/basis/tram.Edge.giv`|
|helsinki/bus|True|`datasets/helsinki/basis/bus.Stop.giv`|`datasets/helsinki/basis/bus.Edge.giv`|
|helsinki/ferry|True|`datasets/helsinki/basis/ferry.Stop.giv`|`datasets/helsinki/basis/ferry.Edge.giv`|
|helsinki/rail|True|`datasets/helsinki/basis/rail.Stop.giv`|`datasets/helsinki/basis/rail.Edge.giv`|
|helsinki/subway|True|`datasets/helsinki/basis/subway.Stop.giv`|`datasets/helsinki/basis/subway.Edge.giv`|
|helsinki/tram|True|`datasets/helsinki/basis/tram.Stop.giv`|`datasets/helsinki/basis/tram.Edge.giv`|
|helsinki/walk|True|`datasets/helsinki/basis/walk.Stop.giv`|`datasets/helsinki/basis/walk.Edge.giv`|
|lowersaxony/existing|False|`datasets/lowersaxony/basis/Existing-Stop.giv`|`datasets/lowersaxony/basis/Existing-Edge.giv`|
|mandl/default|False|`datasets/mandl/basis/Stop.giv`|`datasets/mandl/basis/Edge.giv`|
|mandl-multimodal/line|False|`datasets/mandl-multimodal/basis/line.Stop.giv`|`datasets/mandl-multimodal/basis/line.Edge.giv`|
|mandl-multimodal/ridepooling|False|`datasets/mandl-multimodal/basis/ridepooling.Stop.giv`|`datasets/mandl-multimodal/basis/ridepooling.Edge.giv`|
|ring/default|False|`datasets/ring/basis/Stop.giv`|`datasets/ring/basis/Edge.giv`|
|sioux_falls/default|False|`datasets/sioux_falls/basis/Stop.giv`|`datasets/sioux_falls/basis/Edge.giv`|
|small-multimodal/green|False|`datasets/small-multimodal/basis/green.Stop.giv`|`datasets/small-multimodal/basis/green.Edge.giv`|
|small-multimodal/nonscheduled|False|`datasets/small-multimodal/basis/nonscheduled.Stop.giv`|`datasets/small-multimodal/basis/nonscheduled.Edge.giv`|
|small-multimodal/red|False|`datasets/small-multimodal/basis/red.Stop.giv`|`datasets/small-multimodal/basis/red.Edge.giv`|
|small-path/default|False|`datasets/small-path/basis/Stop.giv`|`datasets/small-path/basis/Edge.giv`|
|toy/default|False|`datasets/toy/basis/Stop.giv`|`datasets/toy/basis/Edge.giv`|
|toy-multimodal/bus|False|`datasets/toy-multimodal/basis/bus.Stop.giv`|`datasets/toy-multimodal/basis/bus.Edge.giv`|
|toy-multimodal/tram|False|`datasets/toy-multimodal/basis/tram.Stop.giv`|`datasets/toy-multimodal/basis/tram.Edge.giv`|

## 下一轮如何继续

先读本文件与 README，再选 view；从 view_registry 取路径，从 effective_config 获取方向/单位。
读取原始文件时保留 source path 和原始行号。比较结果使用相同需求、预算和网络范围。
当前结果可直接复用，但如果 file_manifest 哈希变化，应重跑；绝对路径只代表本次机器，不作为跨电脑定位依据。
