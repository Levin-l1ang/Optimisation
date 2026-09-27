# LinTim 选题导向 EDA

## 先看什么

1. `LinTim_EDA_Report.html`：直接用浏览器打开，中文结论 + 18 张图。
2. `LinTim_EDA.ipynb`：已包含本次执行结果，可逐节阅读和修改。
3. `DATA_MAP.md`：供人和下一轮 AI 分析复用的准确路径与语义说明。

## 如何重新运行（Windows / macOS / Linux）

解压完整项目，进入 `LinTim_EDA` 文件夹，在你选定的 Python 环境执行：

```sh
python -m pip install -r requirements.txt
python -m jupyterlab LinTim_EDA.ipynb
```

VS Code 也可以打开 notebook，选择安装了上述依赖的 Python kernel。
默认 `ROOT = Path("data/openlintim-master")`，所以完整包可以直接运行。
若使用自己的原始数据路径，只改 notebook 首个代码单元的 ROOT。
从头 Restart Kernel + Run All，所有输出写入 `results/`，不会修改原始输入。
本次程序约 20 秒完成；其他机器时间可能不同。数据换成其他版本后，统计部分可复用，选定网络绘图部分可能需要调整名称。

## 项目结构

- `eda_tools.py`：索引、配置继承、读取、检查、图分析、OD 分析和绘图函数。
- `LinTim_EDA.ipynb`：中文探索流程。
- `data/openlintim-master/`：上传的完整仓库快照，保留原始目录、代码和许可证。
- `results/tables/`：全部 CSV 结果及原始路径来源。
- `results/audit/`：完整目录列表、JSON 索引、运行配置与版本。
- `results/figures/`：18 张可单独使用的 PNG。
- `docs/format_reference_excerpt.txt`：上传手册的相关格式说明，保留原文行号。
- `validate_results.py`：路径/哈希、OD 恒等式、方向设置、最短路径和解析边界验证。

## 结果边界

这是上传快照的 EDA，不是优化算法的完整实验。
未提供时刻表/事件活动网络/延误观测，因此没有准点率、真实换乘耗时或时间趋势结论。
没有把源代码和 CI 测试夹具当成研究样本。模板/数据生成目录只记入清单。
不同模式的 ID 只在同一 dataset 内解释；joint 不与各模式相加。
路段上下界保持原始时间单位；边长依据配置转为 km。
network_summary 中 length_km 是边记录长度之和：有向正反向边会分别计入，不是去重物理道路长度。
缺少 OD 记录不补零。线路频率约束不是观测班次或实际运力利用率。
需要 solver 的线路优化留给选题后的阶段；当前 EDA 不需要 solver。

## 验证方式

实际按顺序执行了 notebook 的全部 12 个代码单元并保存输出；构建环境使用 Python 顺序执行和富输出捕获，未安装 Jupyter kernel。
使用 `python validate_results.py` 可在复跑后验证 44 项具体检查。
对 18 张图进行了人工视图检查。

数据使用请保留源仓库许可证以及 Helsinki 数据归属说明，见原始 data 目录。
