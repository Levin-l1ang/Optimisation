function timetable = Start_Here(configfile)

%Diese Datei bild den Rumpf der Verfahren und wird als einzige direkt aufgerufen%
%Angefangen mit der Verarbeitung der Eingabedaten, werden danach
%sequentiell alle 9 M�glichkeiten zur Fahrplangenerierung angesto�en um
%schlie�lich die Fahrpl�ne in txt dateien umzulenken.

%% Datenverarbeitung:

config = Config(configfile);

activities_file = strcat(configfile(1:strfind(configfile,'basis')+4),'/../',config.getStringValue('default_activities_periodic_file'));
events_file = strcat(configfile(1:strfind(configfile,'basis')+4),'/../',config.getStringValue('default_events_periodic_file'));
timetable_file = strcat(configfile(1:strfind(configfile,'basis')+4),'/../',config.getStringValue('default_timetable_periodic_file'));

%Wir lesen zwar die LineIDs aber werden sie intern selber durchnummerieren
if(exist(activities_file,'file') == 0 || exist(events_file,'file') == 0)
	strcat('Either\t',config.getStringValue('default_activities_periodic_file'),' or\t',config.getStringValue('default_events_periodic_file'),' does not exist. Please Check!')
	exit;
end

[activityId, edgetype, tailEventId, headEventId, lowerBound, upperBound, passengers] = textread(activities_file,'%u %s %u %u %f %f %f','delimiter' ,';','endofline','\r\n', 'commentstyle'	,'shell');
%lowerBound=round(lowerBound);
%upperBound=round(upperBound);
passengers=round(passengers);
Activity=[activityId tailEventId headEventId lowerBound upperBound passengers];

[eventId, ~, stopId, lineId, passengers]=textread(events_file,'%u %s %u %u %f','delimiter' ,';','endofline','\r\n', 'commentstyle'	,'shell');
passengers=round(passengers);

Event=[eventId, stopId, lineId, passengers, zeros(size(eventId,1),1), ones(size(eventId,1),1)];
logicheadway = ismember(edgetype,'"headway"');
logicheadway = logicheadway(logicheadway==1);
if(size(logicheadway,1)>0)
	disp('There are ''headway''-activities found. Computing a timetable with these settings assume to not have headways. Recompute the ''ptn2ean'' without headways. Process stopped.')
	exit;
end

period = config.getIntegerValue('period_length');

%In Spalte 5 werden wir den Startzeitpunkt des Events ablegen
%Spalte 6 dient als Platzhalter, falls wir doch mit den Frequenzen arbeiten
%wollen


%% Umnummerierung

%Um die Linien sauber zu trennen, wird jede R�cklinien inkrementiert und
%zwar um den Wert der gr��ten Linien, => rueckfahrt 1 entspricht 1+max

maxline=size(unique(lineId),1); 
%%hier nummerieren wir neu durch:
NEW_lineIDs=zeros(size(lineId));
OLD_IDs=unique(lineId);

for i=1:maxline;
   NEW_LineIDs(find(lineId==OLD_IDs(i)))=i;
end
Event(:,3)=NEW_LineIDs;

rueck=0;
for i=1: size(Event,1)-1
    if(lineId(i)==lineId(i+1)) %Wir also noch in der gleichen Linie sind
        thisedge=ismember([tailEventId headEventId],[i i+1],'rows');
        
        if(isempty(find(thisedge, 1)))%Wir keine Kante gefunden haben=> also die r�ckfahrt betrachten
            rueck=maxline;
        else %Also zwischen zwei Events eine Kante besteht
            
            Event(i+1,5) =Event(i,5)+ Activity(thisedge, 4);%Hier summieren wir die Fahrzeit auf, somit wissen wann welche Linie wo ist
        end
        Event(i+1,3)=Event(i+1,3)+rueck;
        
    else %Wir also eine neue Linie betrachten
        rueck=0;
    end
end
%% Verarbeitung der Changekanten aus der Activity Matrix:
%Wir suchen alle Changekanten mit mehr als 0 Passagieren raus:
changeedges=ismember(edgetype,'"change"');
Changes=Activity(changeedges,:);
Changes=Changes(find(Changes(:,6)),:);

%Als n�chstes bestimmen wir die zugeh�rigen Linien und Zeitpunkte der
%passenden Event:
%In Changes spalte 7 und 8 stehen somit die Linien und Arrival Zeiten
%In Changes spalte 9 und 10 stehen somit die Linien und Departure Zeiten
Changes= [Changes Event(Changes(:,2),3) Event(Changes(:,2),5) Event(Changes(:,3),3) Event(Changes(:,3),5)];
%Die ben�tigten Zeiten f�r den Umstieg befinden sich in der 4ten Spalte

%Somit k�nnen wir bereits unsere Rechenkonstante bestimmen:
%Departure - (Arrival+Change) und diese Landet in Spalte 11
Konstanten=Changes(:,10) - (Changes(:,8)+Changes(:,4));
Changes=[Changes Konstanten];
Changelines=unique([Changes(:,7); Changes(:,9)]);


%% Aufruf vom Rumpf:
%Der Aufruf findet jeweils pro Gewicht statt und iteriert dann �ber die 3
%Verfahren
if(strmatch(config.getStringValue('match-weight'),'w1'))
    	matchweight = 1;
elseif(strmatch(config.getStringValue('match-weight'),'w2'))
    	matchweight = 2;
elseif(strmatch(config.getStringValue('match-weight'),'w3'))
	matchweight = 3;
else
    fprintf('No valid value specified for parameter ''match-weight''. Process stopped.');
    exit;
end

if(strmatch(config.getStringValue('line-matching'),'greedy')==1)
    timetable= begin4(1,matchweight, Event,Changes,[],period);
    fileID = fopen(timetable_file,'w');
    for i=1:size(timetable,1)
       fprintf(fileID, '%u; %u \n', timetable(i,1), timetable(i,2));
    end
elseif(strmatch(config.getStringValue('line-matching'),'matchgreedy')==1)
    timetable= begin4(2,matchweight, Event,Changes,[],period);
    fileID = fopen(timetable_file,'w');
    for i=1:size(timetable,1)
       fprintf(fileID, '%u; %u \n', timetable(i,1), timetable(i,2));
    end
elseif(strmatch(config.getStringValue('line-matching'),'matchperfect')==1)
    timetable= begin4(3,matchweight, Event,Changes,[],period);
    fileID = fopen(timetable_file,'w');
    for i=1:size(timetable,1)
       fprintf(fileID, '%u; %u \n', timetable(i,1), timetable(i,2));
    end
else
    fprintf('No valid value specified for parameter ''line-matching''. Process stopped.');
end

end











