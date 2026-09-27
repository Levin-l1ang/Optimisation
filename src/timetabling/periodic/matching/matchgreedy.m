function [ giveback ] = matchgreedy( Nodes,Matchingedges )
%wir wollen ein Greedy matching durchf�hren und eine i x 2 Tabelle mit zu
% paarenden Knoten zur�ckgeben
%Matchingedges gibt unsere Kantengewichte bzgl der Linien an, dies sind
%aber nicht die Gewichte bez�glich der Knoten im Matchinggrafen, diese werden in Nodeweights
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
%nun suchen wir das maximum, w�hlen das zugeh�rige Linienpaar, Nullen die
%jeweilige Zeile/Spalte und machen weiter bis die Matrix nur aus Nullen
%besteht
giveback=[];
while(sum(sum(Nodeweights))>0)
    %Nun noch die Knoten mit maximalem Kantengewicht raussuchen:
    [row,col]=find(Nodeweights==max(Nodeweights(:)),1);
    giveback=[giveback ; row, col];
    %und die jeweilige Zeile bzw Spalten Nullen:
    Nodeweights(row,:)=0;
    Nodeweights(:,row)=0;
    Nodeweights(col,:)=0;
    Nodeweights(:,col)=0;
end
return;

end

