function [ giveback ] = matchperfect( Nodes, Matchingedges )
%MATCHPERFECT Summary of this function goes here
%   Detailed explanation goes here




%wir wollen ein  minimal-cost-matching durchf�hren und eine i x 2 Tabelle mit zu
% paarenden Knoten zur�ckgeben
%Matchingedges gibt unsere Kantengewichte bzgl der Linien an, dies sind
%aber nicht die Gewichte bez�glich der Knoten im Matchinggrafen(da knoten verschmelzen und so u.U mehr als eine Linie enthalten), diese werden in Nodeweights
%gesammelt
Nodeweights=sparse(size(Nodes,2),size(Nodes,2));
%Nodes{2}=[Nodes{2} Nodes{4}]
[LineA,LineB,Umsteiger]=find(Matchingedges);


%dazu ist es n�tig zu bestimmen welchen Knoten die zZ betrachteten 2 Linien
%angeh�ren um dann den Eintrag in Nodeweights zu setzen
for i=1:size(LineA,1)

        [a,b,bob]=cellfun(@(tim) find(tim==LineA(i)), Nodes, 'UniformOutput', false);
        LineANode=find(cellfun(@isempty,bob)==0); %Wir haben unseren ersten Matrixindex

        [a,b,bob]=cellfun(@(tim) find(tim==LineB(i)), Nodes, 'UniformOutput', false);
        LineBNode=find(cellfun(@isempty,bob)==0); %Wir haben unseren zweiten Matrixindex
        %und k�nnen nun unseren Eintrag vornehmen:
        Nodeweights(LineANode,LineBNode)=Nodeweights(LineANode,LineBNode)+Umsteiger(i);

end

Nodeweights=triu(triu(Nodeweights)+triu(Nodeweights'),1);%von A nach B oder B nach A ist wurscht, aber A nach A brauchen wir nicht
%falls wir auf den Kantenknoten  nur 0 stehen haben, also nichts mehr
%verschmelzen k�nnen, geben wir besser NaN zur�ck!
if(max(Nodeweights(:))<=0)
    row=NaN;
    col=NaN;
    giveback=[row, col];
    return
end

%Um das MCM durch ein bin�res lineares Programm zu l�sen ist es zun�chst
%n�tig die Adjazenzmatrix Nodeweights in eine Inzidenzmatrix zu �berf�hren:
%Jeder nicht-null eintrag in der adjazenzmatrix entspricht einer Kante:
[row, col, value]=find(Nodeweights);

Inzidenz=sparse(size(Nodeweights,1),size(row,1));

for i=1:size(row,1)
   Inzidenz(row(i),i)=1;
   Inzidenz(col(i),i)=1;
end

%um dem ganzen etwas auf die spr�nge zu helfen, w�re eine Startl�sung doch
%vielleicht ganz nett
start=matchgreedy(Nodes, Matchingedges);
startloesung=zeros(size(Inzidenz,2),1);
for i=1:size(start,1)
   muster=zeros(size(Inzidenz,1),1);
   muster(start(i,1))=1;
   muster(start(i,2))=1;
   startloesung=startloesung+ismember(Inzidenz', muster', 'rows');
end

%startloesung;
%sum(startloesung)
%pause


options = optimset('MaxTime',3600, 'MaxIter', 5000);

for i=1:10
    [edges,fval,exitflag,output] = bintprog(-1.*value,Inzidenz,ones(size(Inzidenz,1),1),[],[],startloesung,options);
    
    if(all(startloesung==edges))
        break
    else
        startloesung=edges;
    end
    
end
%Nun wissen wir also welche edges zum MCM geh�ren
row=row.*edges;
col=col.*edges;
row=row(row~=0);
col=col(col~=0);
giveback=[row col];

return;

end



