function [ ChangeAB, ChangeBA ] = Datensammler( LineA, LineB, Changes, giveback )
%Wir pflücken aus den Umsteigekanten lediglich jene heraus, welche LineA
%und LineB betreffen und passen die Daten der aktuellen Situation an:


IndexA=ismember([Changes(:,7) Changes(:,9)],[LineA LineB],'rows');
IndexB=ismember([Changes(:,7) Changes(:,9)],[LineB LineA],'rows');

ChangeAB=Changes(IndexA,:);
ChangeBA=Changes(IndexB,:);

%wir lesen den bisher berechneten Versatz aus und passen die Daten an


ChangeAB(:,11)=ChangeAB(:,11)+giveback(LineB)-giveback(LineA);
ChangeBA(:,11)=ChangeBA(:,11)+giveback(LineA)-giveback(LineB);