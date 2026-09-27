"""Read-only LinTim inventory and EDA. Source files are never changed.
Uses only numpy, pandas, scipy and matplotlib; no optimization solver required.
"""
from pathlib import Path
import csv, json, hashlib, platform, re, math
from datetime import datetime, timezone
from collections import Counter
import numpy as np
import pandas as pd
import matplotlib
import scipy
import matplotlib.pyplot as plt
from scipy.sparse import csr_matrix
from scipy.sparse.csgraph import connected_components, dijkstra

SCHEMAS = {
 'Stop.giv':['stop_id','short_name','long_name','x','y'],
 'Existing-Stop.giv':['stop_id','short_name','long_name','x','y'],
 'Edge.giv':['edge_id','left','right','length','lower','upper'],
 'Existing-Edge.giv':['edge_id','left','right','length','lower','upper'],
 'OD.giv':['origin','destination','customers'],
 'Infrastructure-OD.giv':['origin','destination','customers'],
 'Load.giv':['edge_id','load','lower_frequency','upper_frequency'],
 'Headway.giv':['edge_id','headway'],
 'Pool.giv':['line_id','edge_order','edge_id'],
 'Pool-Cost.giv':['line_id','length','cost'],
 'Stop.giv.geo':['stop_id','latitude','longitude'],
 'Demand.giv':['demand_id','short_name','long_name','x','y','demand'],
 'Demand.giv.geo':['demand_id','latitude','longitude'],
 'Infrastructure-Node.giv':['node_id','name','x','y','modalities'],
 'Infrastructure-Link.giv':['link_id','left','right','length','capacity','modalities','directions'],
 'PTN-Infrastructure-Map.giv':['edge_id','link_order','link_id'],
 'Line-Concept.lin':['line_id','edge_order','edge_id','frequency'],
 'Halt.giv':['stop_id','line_id','min_halt','max_halt'],
 'Modality-Colors.giv':['modality','color']}
TEXT={'short_name','long_name','name','modalities','directions','modality','color'}
KEYS={'Stop.giv':'default_stops_file','Edge.giv':'default_edges_file','OD.giv':'default_od_file',
      'Load.giv':'default_loads_file','Pool.giv':'default_pool_file','Pool-Cost.giv':'default_pool_cost_file',
      'Headway.giv':'default_headways_file','Line-Concept.lin':'default_lines_file'}

def identity(name):
    for kind in sorted(SCHEMAS,key=len,reverse=True):
        if name==kind: return '',kind
        if name.endswith('.'+kind): return name[:-len(kind)-1],kind
    return '', 'unknown'

def rel(path,root):
    try: return Path(path).resolve().relative_to(Path(root).resolve()).as_posix()
    except ValueError: return str(Path(path).resolve())

def read_config(path, root):
    values, provenance, trace = {},{},[]
    def visit(p,stack=()):
        p=p.resolve()
        if p in stack: raise ValueError('Config include cycle: '+str(p))
        for ln,line in enumerate(p.read_text(encoding='utf-8-sig').splitlines(),1):
            line=line.split('#',1)[0].strip()
            if not line: continue
            k,v=line.split(';',1); k=k.strip();v=v.strip().strip('"')
            if k in ('include','include_if_exists'):
                q=(p.parent/v).resolve()
                trace.append({'source':rel(p,root),'line':ln,'include':rel(q,root),'exists':q.exists(),'required':k=='include'})
                if q.exists(): visit(q,stack+(p,))
                elif k=='include': raise FileNotFoundError(q)
            else: values[k]=v;provenance[k]=(rel(p,root),ln)
    visit(Path(path))
    return values,provenance,trace

def cfg(c,key,mode='',default=None):
    return c.get(mode+'.'+key,c.get(key,default)) if mode else c.get(key,default)

def parse_table(p):
    mode,kind=identity(p.name); cols=SCHEMAS.get(kind)
    rows,lines,comments,bad=[],[],[],[]
    for ln,line in enumerate(p.read_text(encoding='utf-8-sig').splitlines(),1):
        if line.lstrip().startswith('#'): comments.append(line.lstrip('# ').strip());continue
        line=line.split('#',1)[0].strip()
        if not line: continue
        # Match LinTim CsvReader: semicolon-delimited with surrounding whitespace.
        tokens=[s.strip().strip('"') for s in line.split(';')]
        if cols is None:
            cols=['column_'+str(i+1) for i in range(len(tokens))]
        if len(tokens)!=len(cols):
            bad.append({'line':ln,'error':'column_count','raw':line});continue
        rows.append(tokens);lines.append(ln)
    if cols is None: cols=['column_1']
    df=pd.DataFrame(rows,columns=cols)
    if kind!='unknown':
        for col in cols:
            if col not in TEXT:
                nums=pd.to_numeric(df[col],errors='coerce')
                invalid=nums.isna() | ~np.isfinite(nums)
                for i in df.index[invalid]: bad.append({'line':lines[i],'error':'invalid_numeric:'+col,'raw':str(df.loc[i,col])})
                df[col]=nums
            else: df[col]=df[col].replace('',pd.NA)
    df['_source_line']=lines
    return df,kind,mode,comments,bad

class Study:
    def __init__(self,root,output,seed=42):
        self.root=Path(root).expanduser().resolve();self.out=Path(output).resolve();self.seed=seed
        if not (self.root/'datasets/Global-Config.cnf').exists(): raise ValueError('ROOT must be the extracted openlintim-master folder')
        if self.out.is_relative_to(self.root): raise ValueError('Put outputs outside the source repository')
        for d in ['tables','figures','audit']: (self.out/d).mkdir(parents=True,exist_ok=True)
        self.tables={};self.views={};self.configs={};self.issues=[];self.figures=[]
    def save(self,name,df):
        df.to_csv(self.out/'tables'/f'{name}.csv',index=False,encoding='utf-8-sig');return df
    def flag(self,scope,check,count,detail='',severity='warning'):
        if count: self.issues.append(dict(scope=scope,check=check,count=int(count),severity=severity,detail=detail))
    def inventory(self):
        rows=[];tree=[]
        for p in sorted(self.root.rglob('*')):
            rp=p.relative_to(self.root);tree.append(rp.as_posix()+('/' if p.is_dir() else ''))
            if not p.is_file():continue
            parts=rp.parts;mode,kind=identity(p.name)
            rows.append(dict(relative_path=rp.as_posix(),absolute_path=str(p),parent=rp.parent.as_posix(),depth=len(parts)-1,
                top_level=parts[0],dataset=parts[1] if len(parts)>2 and parts[0]=='datasets' else '',
                stage=parts[2] if len(parts)>3 and parts[0]=='datasets' else '',modality=mode,kind=kind,
                bytes=p.stat().st_size,sha256=hashlib.sha256(p.read_bytes()).hexdigest()))
        self.files=self.save('file_manifest',pd.DataFrame(rows))
        (self.out/'audit/directory_tree.txt').write_text('\n'.join(tree),encoding='utf-8')
        self.files.to_json(self.out/'audit/file_manifest.json',orient='records',force_ascii=False,indent=2)
        records,trace,paths=[],[],[]
        for p in sorted((self.root/'datasets').glob('*/basis/Config.cnf')):
            ds=p.parent.parent.name;c,prov,tr=read_config(p,self.root);self.configs[ds]=c
            trace.extend(dict(dataset=ds,**x) for x in tr)
            for k,v in c.items():
                records.append(dict(dataset=ds,key=k,value=v,source=prov[k][0],source_line=prov[k][1]))
                if re.search(r'\.(giv|geo|lin|tim|cnf|sta|csv|txt)$',v):
                    q=(p.parent.parent/v).resolve()
                    paths.append(dict(dataset=ds,key=k,configured_value=v,relative_path=rel(q,self.root),absolute_path=str(q),exists=q.is_file(),config_source=prov[k][0],config_line=prov[k][1]))
        self.configuration=self.save('effective_config',pd.DataFrame(records))
        self.save('config_includes',pd.DataFrame(trace));self.save('configured_paths',pd.DataFrame(paths))
        meta=dict(created_utc=datetime.now(timezone.utc).isoformat(),root=str(self.root),seed=self.seed,
                  python=platform.python_version(),pandas=pd.__version__,numpy=np.__version__,scipy=scipy.__version__,matplotlib=matplotlib.__version__,
                  notes='Absolute paths are execution-machine snapshots. Rebuild using ROOT + relative_path. All sources are read-only.')
        (self.out/'audit/run_metadata.json').write_text(json.dumps(meta,indent=2),encoding='utf-8')
        return self.files
    def profile(self):
        profiles,columns,badrows=[],[],[]
        for p in sorted((self.root/'datasets').rglob('*')):
            if not p.is_file() or p.suffix not in ('.giv','.lin','.tim','.geo','.sta'): continue
            rp=rel(p,self.root); df,kind,mode,comments,bad=parse_table(p)
            self.tables[rp]=df
            data=df.drop(columns='_source_line'); ds=p.relative_to(self.root).parts[1]
            profiles.append(dict(path=rp,dataset=ds,modality=mode,kind=kind,rows=len(df),columns=len(data.columns),
                bad_rows=len(set(x['line'] for x in bad)),duplicate_rows=int(data.duplicated().sum()),missing_cells=int(data.isna().sum().sum()),header=' | '.join(comments[:3])))
            self.flag(rp,'parse_error',len(bad),'See parse_issues.csv','error')
            self.flag(rp,'duplicate_rows',data.duplicated().sum())
            for b in bad:badrows.append(dict(path=rp,**b))
            for col in data:
                s=data[col];num=pd.api.types.is_numeric_dtype(s)
                columns.append(dict(path=rp,column=col,dtype=str(s.dtype),rows=len(s),missing=int(s.isna().sum()),unique=int(s.nunique()),
                    min=s.min() if num and len(s) else None,median=s.median() if num and len(s) else None,max=s.max() if num and len(s) else None))
        self.profiles=self.save('table_profile',pd.DataFrame(profiles));self.save('column_profile',pd.DataFrame(columns))
        self.save('parse_issues',pd.DataFrame(badrows,columns=['path','line','error','raw']))
        return self.profiles
    def _get(self,p): return self.tables.get(rel(p,self.root))
    def build_views(self):
        registry=[]
        for ds,c in self.configs.items():
            base=self.root/'datasets'/ds
            candidates=[(p,identity(p.name)[0],'ptn') for p in sorted((base/'basis').glob('*Stop.giv')) if identity(p.name)[1]=='Stop.giv']
            # Follow custom default path, recording alternatives rather than silently replacing them.
            cp=base/c.get('default_stops_file','basis/Stop.giv')
            if cp.exists() and all(p!=cp for p,_,_ in candidates): candidates.append((cp,'configured','ptn'))
            ep=base/'basis/Existing-Stop.giv'
            if ep.exists(): candidates.append((ep,'existing','existing'))
            for sp,mode,typ in candidates:
                label=ds+'/'+(mode or 'default');pref=mode+'.' if mode and mode not in ('existing','configured') else ''
                if typ=='existing': edge=sp.with_name('Existing-Edge.giv')
                elif mode in ('','configured'): edge=base/c.get('default_edges_file','basis/Edge.giv')
                else: edge=sp.with_name(pref+'Edge.giv')
                stops=self._get(sp);edges=self._get(edge)
                if stops is None or edges is None:
                    self.flag(label,'missing_network_pair',1,f'{rel(sp,self.root)} | {rel(edge,self.root)}');continue
                v=dict(dataset=ds,modality=mode,type=typ,stops=stops.copy(),edges=edges.copy(),config=c,
                    stop_path=rel(sp,self.root),edge_path=rel(edge,self.root),directed=str(cfg(c,'ptn_is_undirected',mode,'true')).lower()=='false')
                for kind,key in KEYS.items():
                    if kind in ('Stop.giv','Edge.giv'):continue
                    if typ=='existing': p=base/'__not_applicable__'/kind
                    elif not mode or mode=='configured': p=base/c.get(key,('line-planning/' if kind.endswith('.lin') else 'basis/')+kind)
                    else:
                        p0=Path(c.get(key,('line-planning/' if kind.endswith('.lin') else 'basis/')+kind));p=base/p0.parent/(pref+p0.name)
                    v[kind]=self._get(p);v[kind+'_path']=rel(p,self.root) if p.exists() else None
                v['geo']=self._get(sp.with_name(sp.name+'.geo'))
                self.views[label]=v
                registry.append(dict(view=label,dataset=ds,modality=mode or 'default',network_type=typ,directed=v['directed'],
                    stop_path=v['stop_path'],edge_path=v['edge_path'],**{k.replace('.','_')+'_path':v[k+'_path'] for k in KEYS if k not in ('Stop.giv','Edge.giv')}))
        self.registry=self.save('view_registry',pd.DataFrame(registry));return self.registry
    def analyze(self):
        metrics,odmetrics,access,edgesall,nodesall,pools,loads=[],[],[],[],[],[],[]
        for label,v in self.views.items():
            s,e,c=v['stops'],v['edges'],v['config'];ids=s.stop_id.to_numpy();idx={x:i for i,x in enumerate(ids)};n=len(ids)
            self.flag(label,'duplicate_stop_id',s.stop_id.duplicated().sum(),v['stop_path'],'error')
            self.flag(label,'duplicate_edge_id',e.edge_id.duplicated().sum(),v['edge_path'],'error')
            self.flag(label,'edge_unknown_endpoint',(~e.left.isin(ids)|~e.right.isin(ids)).sum(),v['edge_path'],'error')
            self.flag(label,'edge_invalid_bounds',((e.lower<0)|(e.upper<e.lower)|(e.length<0)).sum(),v['edge_path'],'error')
            self.flag(label,'self_loop',(e.left==e.right).sum(),v['edge_path'])
            if s.stop_id.duplicated().any() or s.stop_id.isna().any(): continue
            valid=e[e.left.isin(ids)&e.right.isin(ids)&e.lower.notna()&(e.lower>=0)].copy()
            self.flag(label,'edges_excluded_from_graph',len(e)-len(valid),'Invalid endpoints or time weights; original rows retained','error')
            pairs={}
            for r in valid.itertuples():
                i,j=idx[r.left],idx[r.right];pairs[i,j]=min(pairs.get((i,j),np.inf),r.lower)
                if not v['directed']:pairs[j,i]=min(pairs.get((j,i),np.inf),r.lower)
            ij=list(pairs);rr=[x[0] for x in ij];cc=[x[1] for x in ij]
            A=csr_matrix((np.ones(len(ij)),(rr,cc)),shape=(n,n))
            W=csr_matrix(([pairs[x] for x in ij],(rr,cc)),shape=(n,n))
            weak,lab=connected_components(A,directed=v['directed'],connection='weak');strong,_=connected_components(A,directed=v['directed'],connection='strong')
            degree=np.diff(A.indptr);indeg=np.asarray((A!=0).sum(axis=0)).ravel();isolates=((degree+indeg)==0).sum()
            v.update(A=A,W=W,index=idx,ids=ids,labels=lab)
            components=np.bincount(lab)
            metrics.append(dict(view=label,dataset=v['dataset'],modality=v['modality'] or 'default',network_type=v['type'],directed=v['directed'],
                stops=n,edge_records=len(e),graph_arcs=A.nnz,weak_components=weak,strong_components=strong,isolates=int(isolates),
                largest_component_share=components.max()/n if n else np.nan,mean_out_degree=degree.mean(),max_out_degree=degree.max(),
                simple_density=(A.nnz-int(np.count_nonzero(A.diagonal())))/(n*(n-1)) if n>1 else 0,
                length_km=float((e.length*float(c['gen_conversion_length'])).sum()),period_raw=float(c['period_length']),
                time_units_per_minute=float(c['time_units_per_minute']),stop_source=v['stop_path'],edge_source=v['edge_path']))
            for i,row in s.iterrows():
                nodesall.append(dict(view=label,stop_id=row.stop_id,name=row.long_name,out_degree=degree[i],in_degree=indeg[i],component=int(lab[i]),source=v['stop_path'],source_line=row._source_line))
            edgeview=e.copy();edgeview['length_km']=e.length*float(c['gen_conversion_length']);edgeview['slack_raw']=e.upper-e.lower
            edgeview['view']=label;edgeview['source']=v['edge_path'];edgesall.append(edgeview)
            od=v['OD.giv']
            if od is not None:
                self.flag(label,'duplicate_od_pair',od.duplicated(['origin','destination']).sum(),v['OD.giv_path'],'error')
                self.flag(label,'negative_od',(od.customers<0).sum(),v['OD.giv_path'],'error')
                self.flag(label,'od_unknown_stop',(~od.origin.isin(ids)|~od.destination.isin(ids)).sum(),v['OD.giv_path'],'error')
                good=od[od.origin.isin(ids)&od.destination.isin(ids)&(od.customers>=0)].copy()
                unique=good.drop_duplicates(['origin','destination'])
                positive=good[good.customers>0]
                total=positive.customers.sum();sorted_d=positive.customers.sort_values(ascending=False)
                odmetrics.append(dict(view=label,rows=len(od),observed_pairs=len(unique),possible_pairs=n*n,record_coverage=len(unique)/(n*n),
                    missing_pairs=n*n-len(unique),explicit_zero_pairs=int((unique.customers==0).sum()),positive_pairs=int((unique.customers>0).sum()),
                    total_recorded_demand=total,top10pct_positive_pair_share=sorted_d.iloc[:max(1,math.ceil(len(sorted_d)*.1))].sum()/total if total else np.nan,
                    diagonal_demand=good.loc[good.origin==good.destination,'customers'].sum(),source=v['OD.giv_path']))
                # Exact shortest paths for every origin with positive observed demand; avoid a dense N x N matrix.
                if not od.duplicated(['origin','destination']).any():
                    for origin,g in positive.groupby('origin'):
                        dist=dijkstra(W,directed=True,indices=idx[origin]);vals=dist[[idx[x] for x in g.destination]]
                        for r,tt in zip(g.itertuples(),vals):access.append(dict(view=label,origin=r.origin,destination=r.destination,demand=r.customers,
                            reachable=bool(np.isfinite(tt)),lower_bound_raw=float(tt) if np.isfinite(tt) else np.nan,od_source=v['OD.giv_path'],edge_source=v['edge_path']))
                else:self.flag(label,'accessibility_skipped_duplicate_od',1,'Resolve duplicate OD pairs before summing demand','error')
            pool=v['Pool.giv'];cost=v['Pool-Cost.giv'];load=v['Load.giv'];concept=v['Line-Concept.lin']
            if pool is not None:
                self.flag(label,'pool_unknown_edge',(~pool.edge_id.isin(e.edge_id)).sum(),v['Pool.giv_path'],'error')
                self.flag(label,'pool_duplicate_order',pool.duplicated(['line_id','edge_order']).sum(),v['Pool.giv_path'],'error')
                if not e.edge_id.duplicated().any():
                    merged=pool.merge(e[['edge_id','left','right','length']],on='edge_id',how='left',validate='many_to_one')
                    for lid,g in merged.groupby('line_id'):
                        g=g.sort_values('edge_order');order_ok=list(g.edge_order)==list(range(1,len(g)+1))
                        possible=None
                        for r in g.itertuples():
                            choices=[(r.left,r.right)] if v['directed'] else [(r.left,r.right),(r.right,r.left)]
                            possible={b for a,b in choices if possible is None or a in possible}
                        contiguous=bool(possible)
                        self.flag(label,'pool_noncontiguous_line',not contiguous,f'line_id={lid}')
                        self.flag(label,'pool_nonconsecutive_order',not order_ok,f'line_id={lid}')
                        pools.append(dict(view=label,line_id=lid,edge_count=len(g),length_km=g.length.sum()*float(c['gen_conversion_length']),contiguous=contiguous,source=v['Pool.giv_path']))
                if cost is not None:
                    self.flag(label,'pool_line_without_cost',len(set(pool.line_id)-set(cost.line_id)),v['Pool-Cost.giv_path'])
                    self.flag(label,'cost_without_pool_line',len(set(cost.line_id)-set(pool.line_id)),v['Pool-Cost.giv_path'])
            for kind,key,parent,parent_key in [('Load.giv','edge_id',e,'edge_id'),('Headway.giv','edge_id',e,'edge_id'),('Pool-Cost.giv','line_id',pool,'line_id')]:
                df=v[kind]
                if df is not None:
                    self.flag(label,kind+'_duplicate_key',df[key].duplicated().sum(),v[kind+'_path'],'error')
                    if parent is not None:self.flag(label,kind+'_orphan_key',(~df[key].isin(parent[parent_key])).sum(),v[kind+'_path'],'error')
            if load is not None:
                self.flag(label,'invalid_load_bounds',((load.load<0)|(load.lower_frequency<0)|(load.upper_frequency<load.lower_frequency)).sum(),v['Load.giv_path'],'error')
                z=load.copy();z['view']=label;z['source']=v['Load.giv_path'];loads.append(z)
            if concept is not None:
                self.flag(label,'line_concept_unknown_edge',(~concept.edge_id.isin(e.edge_id)).sum(),v['Line-Concept.lin_path'],'error')
                self.flag(label,'inconsistent_line_frequency',(concept.groupby('line_id').frequency.nunique()>1).sum(),v['Line-Concept.lin_path'],'error')
        self.network=self.save('network_summary',pd.DataFrame(metrics));self.od=self.save('od_summary',pd.DataFrame(odmetrics))
        self.access=self.save('od_accessibility',pd.DataFrame(access));self.edges=self.save('edges_enriched',pd.concat(edgesall,ignore_index=True))
        self.nodes=self.save('node_metrics',pd.DataFrame(nodesall));self.pool=self.save('line_pool_summary',pd.DataFrame(pools))
        self.loads=self.save('edge_loads',pd.concat(loads,ignore_index=True) if loads else pd.DataFrame())
        return self.network
    def multimodal(self):
        overlaps,infra,checks,demandrows=[],[],[],[]
        for ds,c in self.configs.items():
            modes=[x.strip() for x in c.get('modalities_all','').strip('[]').split(',') if x.strip()]
            vv={m:self.views[ds+'/'+m] for m in modes if ds+'/'+m in self.views}
            union=set().union(*(set(v['ids']) for v in vv.values())) if vv else set()
            for a,va in vv.items():
                for b,vb in vv.items():
                    sa,sb=set(va['ids']),set(vb['ids']);common=sa&sb
                    overlaps.append(dict(dataset=ds,mode_a=a,mode_b=b,shared_stops=len(common),jaccard=len(common)/len(sa|sb),
                        source_a=va['stop_path'],source_b=vb['stop_path']))
                    if a<b and common:
                        x=va['stops'].set_index('stop_id').loc[sorted(common),['x','y']];y=vb['stops'].set_index('stop_id').loc[sorted(common),['x','y']]
                        self.flag(ds,'shared_id_coordinate_disagreement',(~np.isclose(x,y,rtol=0,atol=1e-6).all(axis=1)).sum(),a+' vs '+b)
            base=self.root/'datasets'/ds/'basis';od=self._get(base/'Infrastructure-OD.giv');nodes=self._get(base/'Infrastructure-Node.giv');links=self._get(base/'Infrastructure-Link.giv')
            if od is not None:
                self.flag(ds,'infrastructure_od_duplicate_pair',od.duplicated(['origin','destination']).sum(),rel(base/'Infrastructure-OD.giv',self.root),'error')
                universe=set(nodes.node_id) if nodes is not None else union
                infra.append(dict(dataset=ds,rows=len(od),positive_rows=int((od.customers>0).sum()),total_recorded_demand=od.customers.sum(),
                    endpoint_universe='Infrastructure-Node.giv' if nodes is not None else 'union_of_modality_stops',
                    unknown_endpoint_rows=int((~od.origin.isin(universe)|~od.destination.isin(universe)).sum()),source=rel(base/'Infrastructure-OD.giv',self.root)))
            for mode,v in vv.items():
                mp=self._get(base/(mode+'.PTN-Infrastructure-Map.giv'))
                if mp is not None:
                    self.flag(ds+'/'+mode,'map_unknown_ptn_edge',(~mp.edge_id.isin(v['edges'].edge_id)).sum())
                    if links is not None:self.flag(ds+'/'+mode,'map_unknown_infrastructure_link',(~mp.link_id.isin(links.link_id)).sum())
            demand=self._get(base/'Demand.giv')
            if demand is not None:
                dd=demand.copy();dd['dataset']=ds;dd['source']=rel(base/'Demand.giv',self.root);demandrows.append(dd)
        self.overlap=self.save('modality_overlap',pd.DataFrame(overlaps));self.infra=self.save('infrastructure_od_summary',pd.DataFrame(infra))
        self.demand=self.save('demand_points',pd.concat(demandrows,ignore_index=True) if demandrows else pd.DataFrame())
        return self.overlap
    def robustness(self,labels=None,repeats=20):
        """Static out-degree attack vs repeated random deletion, on weak connectivity only."""
        if labels is None:labels=[k for k,v in self.views.items() if 10<=len(v['ids'])<=400 and v['type']=='ptn' and v['modality']=='']
        rows=[];fractions=[0,.05,.1,.2,.3];rng=np.random.default_rng(self.seed)
        for label in labels:
            v=self.views[label];a=v['A'];n=a.shape[0];rank=np.lexsort((np.arange(n),-np.diff(a.indptr)))
            for method in ['static_out_degree','random']:
                for trial in range(repeats if method=='random' else 1):
                    order=rng.permutation(n) if method=='random' else rank
                    for f in fractions:
                        k=int(n*f);keep=np.ones(n,bool);keep[order[:k]]=False;sub=a[keep][:,keep]
                        _,lab=connected_components(sub,directed=True,connection='weak')
                        lcc=np.bincount(lab).max() if len(lab) else 0
                        rows.append(dict(view=label,method=method,trial=trial,requested_fraction=f,removed_count=k,actual_fraction=k/n,
                            largest_component_over_original_n=lcc/n,edge_source=v['edge_path'],stop_source=v['stop_path']))
        self.robust=self.save('robustness_trials',pd.DataFrame(rows));return self.robust
    def extensions(self):
        """Coverage, OD endpoints, coordinates and actual line-concept constraint checks."""
        coverage,station_demand,service,coordinates,costrows,paths=[],[],[],[],[],[]
        for label,v in self.views.items():
            e,s,pool,od=v['edges'],v['stops'],v['Pool.giv'],v['OD.giv']
            if pool is not None:
                counts=pool.groupby('edge_id').line_id.nunique()
                for r in e.itertuples():coverage.append(dict(view=label,edge_id=r.edge_id,candidate_lines=int(counts.get(r.edge_id,0)),edge_source=v['edge_path'],pool_source=v['Pool.giv_path']))
            if od is not None:
                out=od.groupby('origin').customers.sum();inc=od.groupby('destination').customers.sum()
                for r in s.itertuples():station_demand.append(dict(view=label,stop_id=r.stop_id,name=r.long_name,outgoing_recorded=out.get(r.stop_id,np.nan),incoming_recorded=inc.get(r.stop_id,np.nan),source=v['OD.giv_path']))
            if v['Pool-Cost.giv'] is not None:
                d=v['Pool-Cost.giv'].copy();d['view']=label;d['length_km']=d.length*float(v['config']['gen_conversion_length']);d['source']=v['Pool-Cost.giv_path'];costrows.append(d)
            concept=v['Line-Concept.lin'];load=v['Load.giv']
            if concept is not None and load is not None:
                # One frequency contribution per line-edge occurrence; repeated traversals count.
                freq=concept.groupby('edge_id').frequency.sum()
                for r in load.itertuples():
                    f=freq.get(r.edge_id,0)
                    service.append(dict(view=label,edge_id=r.edge_id,concept_frequency=f,lower=r.lower_frequency,upper=r.upper_frequency,
                        within_bounds=bool(r.lower_frequency<=f<=r.upper_frequency),concept_source=v['Line-Concept.lin_path'],load_source=v['Load.giv_path']))
            geo=v['geo']
            if geo is not None:
                self.flag(label,'geo_unknown_stop',(~geo.stop_id.isin(s.stop_id)).sum())
                self.flag(label,'geo_invalid_range',((geo.latitude.abs()>90)|(geo.longitude.abs()>180)).sum())
                self.flag(label,'geo_duplicate_stop',geo.stop_id.duplicated().sum())
            # Preserve exact coordinate mismatches with source references, not just warning counts.
        for ds in self.configs:
            vv={v['modality']:v for v in self.views.values() if v['dataset']==ds and v['modality'] not in ('','joint','existing')}
            for a,va in vv.items():
                for b,vb in vv.items():
                    if a>=b:continue
                    x=va['stops'].set_index('stop_id');y=vb['stops'].set_index('stop_id');common=x.index.intersection(y.index)
                    for sid in common:
                        dx=float(x.loc[sid,'x']-y.loc[sid,'x']);dy=float(x.loc[sid,'y']-y.loc[sid,'y'])
                        if abs(dx)>1e-6 or abs(dy)>1e-6:
                            coordinates.append(dict(dataset=ds,mode_a=a,mode_b=b,stop_id=sid,x_a=x.loc[sid,'x'],y_a=x.loc[sid,'y'],x_b=y.loc[sid,'x'],y_b=y.loc[sid,'y'],distance_raw=np.hypot(dx,dy),source_a=va['stop_path'],source_b=vb['stop_path']))
        self.coverage=self.save('line_pool_edge_coverage',pd.DataFrame(coverage))
        self.station_demand=self.save('station_demand',pd.DataFrame(station_demand))
        self.service=self.save('line_concept_constraint_check',pd.DataFrame(service))
        if len(self.service):
            for label,g in self.service.groupby('view'):
                self.flag(label,'stored_concept_frequency_outside_bounds',(~g.within_bounds).sum(),'Compare supplied Line-Concept.lin and Load.giv; may reflect different planning states')
        self.coordinates=self.save('coordinate_mismatches',pd.DataFrame(coordinates))
        self.costs=self.save('line_costs',pd.concat(costrows,ignore_index=True) if costrows else pd.DataFrame())
        # Exactly summarized reachable observed demand; never infer unrecorded demand.
        summary=[]
        for label,g in self.access.groupby('view'):
            reachable=g[g.reachable];total=g.demand.sum()
            summary.append(dict(view=label,positive_recorded_demand=total,unreachable_demand=g.loc[~g.reachable,'demand'].sum(),
                reachable_demand_share=reachable.demand.sum()/total if total else np.nan,
                weighted_lower_bound_raw=np.average(reachable.lower_bound_raw,weights=reachable.demand) if len(reachable) else np.nan))
        self.save('accessibility_summary',pd.DataFrame(summary))
        # Modality-specific OD totals can count separate legs, so no demand-conservation assumption.
        fig,axs=plt.subplots(1,2,figsize=(12,5))
        for label,g in self.costs.groupby('view'):axs[0].scatter(g.length_km,g.cost,s=13,alpha=.6,label=label)
        axs[0].set(xlabel='Candidate line length (km)',ylabel='Stored line cost (dataset units)');axs[0].legend(fontsize=7)
        cov=self.coverage.groupby('view').candidate_lines.agg(lambda x: (x>0).mean());axs[1].barh(cov.index,cov.values);axs[1].set_xlabel('Fraction of edges in at least one candidate line')
        self.figure('15_pool_cost_coverage',fig,'Costs need not share monetary units across datasets. Coverage does not guarantee demand coverage or a feasible frequency solution.')
        fig,axs=plt.subplots(1,2,figsize=(12,5))
        for ax,label in zip(axs,['athens/default','goevb/default']):
            d=self.station_demand[self.station_demand.view==label].nlargest(12,'outgoing_recorded').sort_values('outgoing_recorded');ax.barh(d.stop_id.astype(int).astype(str),d.outgoing_recorded,color='#398a9e');ax.set(title=label,xlabel='Recorded outgoing demand',ylabel='Stop ID')
        self.figure('16_demand_hubs',fig,'Top origins by recorded OD demand; full stop names and inbound/outbound values are in station_demand.csv.')
        fig,axs=plt.subplots(1,2,figsize=(12,4))
        for ax,ds in zip(axs,['helsinki','lowersaxony']):
            d=self.demand[self.demand.dataset==ds].sort_values('demand',ascending=False);ax.bar(range(min(20,len(d))),d.demand.head(20));ax.set(title=ds,xlabel='Demand point rank (top 20)',ylabel='Stored point demand')
        self.figure('17_point_demand',fig,'Demand.giv describes point demand, distinct from OD.giv. Helsinki point coordinates and modal stop coordinates require CRS verification before spatial joining.')
        fig,axs=plt.subplots(1,2,figsize=(12,4))
        for label in ['athens/default','goevb/default','mandl/default','ring/default']:
            e=self.edges[self.edges.view==label];vals=np.sort(e.slack_raw);axs[0].step(vals,np.arange(1,len(vals)+1)/len(vals),label=label)
        axs[0].set(xlabel='Edge upper minus lower bound (raw time units)',ylabel='ECDF');axs[0].legend(fontsize=7)
        if len(self.service):
            d=self.service;axs[1].plot(d.edge_id,d.concept_frequency,'o-',label='Stored concept');axs[1].plot(d.edge_id,d.lower,'--',label='Minimum');axs[1].plot(d.edge_id,d.upper,':',label='Maximum');axs[1].set(xlabel='BOMHarbour edge ID',ylabel='Frequency');axs[1].legend()
        self.figure('18_time_slack_service',fig,'Bounds express planning flexibility, not measured variability. The only supplied Line-Concept.lin is checked against its stored load frequency constraints.')
        return self.coverage

    def finish_audit(self):
        self.quality=self.save('quality_issues',pd.DataFrame(self.issues,columns=['scope','check','count','severity','detail']))
        return self.quality
    def figure(self,name,fig,caption):
        fig.tight_layout();p=self.out/'figures'/f'{name}.png';fig.savefig(p,dpi=155,bbox_inches='tight');plt.close(fig)
        self.figures.append(dict(file=p.name,title=name,caption=caption));return p
    def plots(self):
        plt.rcParams.update({'axes.spines.top':False,'axes.spines.right':False,'font.size':9,'figure.facecolor':'white'})
        # Files and availability.
        fig,ax=plt.subplots(figsize=(10,4));cnt=self.files[self.files.dataset!=''].groupby('dataset').size();cnt.plot.bar(ax=ax,color='#3a8a9e');ax.set_ylabel('All files (including config / Makefile)');self.figure('01_file_inventory',fig,'Directory inventory; file count is not sample count.')
        kinds=['Stop.giv','Edge.giv','OD.giv','Load.giv','Pool.giv','Pool-Cost.giv','Infrastructure-OD.giv','Demand.giv','Line-Concept.lin']
        av=self.profiles.pivot_table(index='dataset',columns='kind',values='rows',aggfunc='size',fill_value=0).reindex(index=sorted(self.configs),columns=kinds,fill_value=0)
        fig,ax=plt.subplots(figsize=(11,6));im=ax.imshow(av.values,aspect='auto',cmap='Blues');ax.set_yticks(range(len(av)),av.index);ax.set_xticks(range(len(kinds)),kinds,rotation=40,ha='right');
        for (i,j),x in np.ndenumerate(av.values):ax.text(j,i,str(x),ha='center',va='center',color='white' if x>3 else 'black')
        fig.colorbar(im,ax=ax,label='Number of files');self.figure('02_data_availability',fig,'Zero means absent, not zero-valued data. Modalities create multiple files of the same role.')
        net=self.network.sort_values('stops');fig,axs=plt.subplots(1,2,figsize=(13,9));axs[0].barh(net.view,net.stops,color='#398a9e');axs[0].set_xscale('log');axs[0].set_xlabel('Stops (log scale)');axs[1].barh(net.view,net.largest_component_share,color='#df9858');axs[1].set_xlabel('Largest weak component / all stops');self.figure('03_network_comparison',fig,'Every row is a dataset + modality/view. Views are not independent samples; existing and joint networks stay separate.')
        chosen=[k for k in ['athens/default','goevb/default','ring/default','mandl/default','sioux_falls/default','helsinki/subway'] if k in self.views]
        fig,axs=plt.subplots(2,3,figsize=(14,9))
        from matplotlib.collections import LineCollection
        for ax,label in zip(axs.flat,chosen):
            v=self.views[label];s=v['stops'].set_index('stop_id');e=v['edges'];pos=s[['x','y']].to_dict('index');segs=[[(pos[r.left]['x'],pos[r.left]['y']),(pos[r.right]['x'],pos[r.right]['y'])] for r in e.itertuples() if r.left in pos and r.right in pos]
            ax.add_collection(LineCollection(segs,colors='#a6b9c2',linewidths=.6));ax.scatter(s.x,s.y,s=8,color='#15758a');ax.autoscale();ax.set_title(label);ax.set_aspect('equal');ax.set_xticks([]);ax.set_yticks([])
        self.figure('04_network_layouts',fig,'Source x/y coordinates, not a geographic basemap. Directed edges drawn without arrows for legibility; direction is respected in metrics.')
        fig,axs=plt.subplots(1,2,figsize=(12,4))
        for label in chosen[:5]:
            vals=np.sort(self.nodes.loc[self.nodes.view==label,'out_degree']);axs[0].step(vals,np.arange(1,len(vals)+1)/len(vals),label=label);e=self.edges[self.edges.view==label];x=np.sort(e.length_km);axs[1].step(x,np.arange(1,len(x)+1)/len(x),label=label)
        axs[0].set(xlabel='Out-degree (undirected: degree)',ylabel='ECDF');axs[1].set(xlabel='Edge length (km, config conversion)',ylabel='ECDF');axs[0].legend(fontsize=7);self.figure('05_degree_length_distributions',fig,'Empirical cumulative distributions compare differently sized networks without histogram-bin artifacts.')
        fig,axs=plt.subplots(1,2,figsize=(12,5));o=self.od.sort_values('total_recorded_demand');axs[0].barh(o.view,o.total_recorded_demand,color='#398a9e');axs[0].set_xscale('symlog');axs[0].set_xlabel('Recorded OD demand; original planning period');axs[1].barh(o.view,o.record_coverage,color='#df9858');axs[1].set_xlabel('Observed OD pairs / N² (diagonal included)');self.figure('06_od_coverage_demand',fig,'Demand totals are not directly comparable between planning periods or split/joint modalities. Missing OD pairs remain unknown.')
        fig,ax=plt.subplots(figsize=(8,5))
        for label in chosen[:5]:
            od=self.views[label]['OD.giv']
            if od is None:continue
            vals=np.sort(od.loc[od.customers>0,'customers'].to_numpy())[::-1]
            if vals.sum()>0:ax.plot(np.arange(1,len(vals)+1)/len(vals),np.cumsum(vals)/vals.sum(),label=label)
        ax.plot([0,1],[0,1],color='gray',ls='--');ax.set(xlabel='Fraction of positive recorded OD pairs (largest first)',ylabel='Cumulative share of recorded demand');ax.legend();self.figure('07_demand_concentration',fig,'A steep curve suggests that a small set of OD pairs dominates demand; zero and unrecorded pairs are excluded.')
        fig,axs=plt.subplots(1,3,figsize=(14,4))
        for ax,label in zip(axs,['athens/default','mandl/default','sioux_falls/default']):
            v=self.views[label];od=v['OD.giv'];mat=od.pivot(index='origin',columns='destination',values='customers').reindex(index=v['ids'],columns=v['ids']);cm=plt.colormaps['viridis'].copy();cm.set_bad('#efb4c1');im=ax.imshow(np.ma.masked_invalid(np.log1p(mat.to_numpy())),cmap=cm,aspect='auto');ax.set_title(label);ax.set(xlabel='Destination, stop-ID order',ylabel='Origin, stop-ID order');fig.colorbar(im,ax=ax,label='log(1 + recorded demand)')
        self.figure('08_od_heatmaps',fig,'Pink denotes missing pairs; numeric zero is a valid observed value. Axes use each file’s stop-ID order.')
        fig,axs=plt.subplots(2,3,figsize=(13,7))
        for ax,label in zip(axs.flat,chosen[:5]):
            a=self.access[(self.access.view==label)&self.access.reachable].sort_values('lower_bound_raw')
            if len(a):ax.plot(a.lower_bound_raw,a.demand.cumsum()/a.demand.sum(),color='#398a9e')
            units=self.views[label]['config']['time_units_per_minute']
            ax.set(title=label+' (units/min='+units+')',xlabel='Lower bound (raw time units)',ylabel='Demand-weighted CDF')
        axs.flat[-1].set_visible(False)
        self.figure('09_accessibility_lower_bounds',fig,'Each panel retains its own raw time scale. Network-only lower bounds omit waiting, transfers and schedules; do not rank datasets by these raw values.')
        fig,ax=plt.subplots(figsize=(10,5));labels=self.pool.view.unique();ax.boxplot([self.pool.loc[self.pool.view==k,'edge_count'] for k in labels],tick_labels=labels,showfliers=False);ax.tick_params(axis='x',rotation=40);ax.set_ylabel('Edges per candidate line');self.figure('10_line_pool_complexity',fig,'Candidate line pools are planning inputs, not automatically operated services. Outliers hidden in this plot remain in the tables.')
        fig,ax=plt.subplots(figsize=(9,5))
        for label,g in self.loads.groupby('view'):ax.scatter(g.lower_frequency,g.load,s=12,alpha=.6,label=label)
        ax.set(xlabel='Minimum total line frequency constraint',ylabel='Stored edge load',yscale='symlog');ax.legend(fontsize=7,ncol=2);self.figure('11_load_frequency_constraints',fig,'Stored Load.giv can be model-derived. Frequency bounds are constraints, not actual observed service or capacity utilization.')
        fig,axs=plt.subplots(1,2,figsize=(12,5))
        for ax,ds in zip(axs,['helsinki','grid-multimodal']):
            m=self.overlap[self.overlap.dataset==ds].pivot(index='mode_a',columns='mode_b',values='shared_stops');im=ax.imshow(m,cmap='Blues');ax.set_xticks(range(len(m)),m.columns,rotation=45);ax.set_yticks(range(len(m)),m.index);ax.set_title(ds)
            for (i,j),x in np.ndenumerate(m.values):ax.text(j,i,str(x),ha='center',va='center',fontsize=8,color='white' if x>m.values.max()/2 else 'black')
        self.figure('12_multimodal_overlap',fig,'Shared stop IDs are candidate physical transfer locations under the LinTim convention, not evidence of a feasible scheduled transfer.')
        fig,axs=plt.subplots(math.ceil(self.robust.view.nunique()/3),3,figsize=(14,11))
        for ax in axs.flat[self.robust.view.nunique():]: ax.set_visible(False)
        for ax,label in zip(axs.flat,self.robust.view.unique()):
            d=self.robust[self.robust.view==label]
            for method,g in d.groupby('method'):
                agg=g.groupby('actual_fraction').largest_component_over_original_n.agg(['mean','std','min','max']);ax.plot(agg.index,agg['mean'],label=method)
                if method=='random':ax.fill_between(agg.index,agg['min'],agg['max'],alpha=.2)
            ax.set_title(label);ax.set(xlabel='Actual removed node fraction',ylabel='Largest weak component / original N')
        axs.flat[0].legend(fontsize=7);self.figure('13_connectivity_robustness',fig,'Exploratory experiment: static out-degree ranking vs 20 random deletion orders (seed 42); band is min–max, not a confidence interval. Weak connectivity ignores service and OD losses.')
        if len(self.quality):
            counts=self.quality.groupby('check')['count'].sum().sort_values().tail(15);fig,ax=plt.subplots(figsize=(10,5));counts.plot.barh(ax=ax,color='#c77568');ax.set_xlabel('Flagged occurrences (checks can overlap)');self.figure('14_quality_flags',fig,'Flags identify items to inspect, not automatically invalid data; original files and line numbers remain available.')
        self.save('figure_index',pd.DataFrame(self.figures));return self.figures

def run(root,output):
    s=Study(root,output);s.inventory();s.profile();s.build_views();s.analyze();s.multimodal();s.robustness();s.extensions();s.finish_audit();s.plots();return s

class FigureFile:
    """Small rich-display object; Jupyter displays the saved PNG without extra dependencies."""
    def __init__(self,path): self.path=Path(path)
    def _repr_png_(self): return self.path.read_bytes()
