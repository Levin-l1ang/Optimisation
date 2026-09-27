"""Targeted validation; run after notebook: python validate_results.py."""
from pathlib import Path
import tempfile, hashlib
import pandas as pd
import numpy as np
from scipy.sparse.csgraph import floyd_warshall, dijkstra
from eda_tools import read_config, parse_table, Study

HERE=Path(__file__).resolve().parent
ROOT=HERE/'data/openlintim-master'
T=HERE/'results/tables'
checks=[]
def check(condition,label):
    if not condition:raise AssertionError(label)
    checks.append(label)

# Every recorded input resolves and retains its content hash.
m=pd.read_csv(T/'file_manifest.csv')
check(all((ROOT/r.relative_path).is_file() and hashlib.sha256((ROOT/r.relative_path).read_bytes()).hexdigest()==r.sha256 for r in m.itertuples()),'All manifest paths and hashes match inputs')
profile=pd.read_csv(T/'table_profile.csv')
check(profile.bad_rows.sum()==0,'All supplied domain tables parse without rejected records')
check(len(profile)==148,'Uploaded snapshot has 148 domain tables')
net=pd.read_csv(T/'network_summary.csv').set_index('view')
check(len(net)==27,'27 separately scoped network views')
check(net.loc['goevb/default','directed']==True,'Directed goevb configuration respected')
check(net.loc['athens/default','time_units_per_minute']==10,'Athens time scale retained')
check(net.loc['grid-multimodal/joint','edge_records']==79 and net.loc['grid-multimodal/joint','graph_arcs']==80,'Parallel joint edges retained in input and collapsed in simple graph')
# Independent identities for OD coverage / total demand, without using analysis objects.
od=pd.read_csv(T/'od_summary.csv')
for r in od.itertuples():
    raw,_,_,_,bad=parse_table(ROOT/r.source)
    check(np.isclose(raw.customers[raw.customers>0].sum(),r.total_recorded_demand),'OD total '+r.view)
    check(r.observed_pairs+r.missing_pairs==r.possible_pairs,'OD coverage identity '+r.view)
a=pd.read_csv(T/'od_accessibility.csv')
# The small-path dataset is a tree: the only route 1 -> 4 traverses three edges.
s,e=ROOT/'datasets/small-path/basis/Stop.giv',ROOT/'datasets/small-path/basis/Edge.giv'
edges=parse_table(e)[0]
expected=edges.lower.sum()
check(np.isclose(a.loc[(a.view=='small-path/default')&(a.origin==1)&(a.destination==4),'lower_bound_raw'].iloc[0],expected),'Small-path end-to-end lower bound matches unique-route sum')
# Compare independent all-pairs and single-source algorithms on the supplied toy graph.
from scipy.sparse import csr_matrix
et=parse_table(ROOT/'datasets/toy/basis/Edge.giv')[0]
rr=list(et.left.astype(int)-1)+list(et.right.astype(int)-1);cc=list(et.right.astype(int)-1)+list(et.left.astype(int)-1)
w=csr_matrix((list(et.lower)*2,(rr,cc)),shape=(8,8))
check(np.allclose(dijkstra(w,directed=True),floyd_warshall(w,directed=True)),'Toy shortest paths agree with Floyd–Warshall')
# Include resolution, overwrite order, optional includes and malformed row evidence.
with tempfile.TemporaryDirectory() as temp:
    d=Path(temp);(d/'nested').mkdir();(d/'a.cnf').write_text('k; 1\ninclude; "nested/b.cnf"\nk; 3\ninclude_if_exists; "missing.cnf"\n')
    (d/'nested/b.cnf').write_text('k; 2\nx; 7\n');c,prov,tr=read_config(d/'a.cnf',d)
    check(c=={'k':'3','x':'7'} and prov['k'][1]==3,'Config relative include and last assignment semantics')
    (d/'OD.giv').write_text('# comment\n1; 2; 3\n2; 3\n3; 1; invalid\n')
    df,_,_,_,bad=parse_table(d/'OD.giv')
    check({x['line'] for x in bad}=={3,4},'Malformed rows and invalid numerics retain original line numbers')
figs=list((HERE/'results/figures').glob('*.png'))
check(len(figs)==18,'18 saved exploration figures')
print('\n'.join('PASS '+x for x in checks))
print(f'\n{len(checks)} checks passed.')
