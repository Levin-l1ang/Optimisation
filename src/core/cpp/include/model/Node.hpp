/**
 * The template for a node object for a graph structure.
 */
#ifndef NODE_HPP
#define NODE_HPP

class Node{
public:
    virtual int getId() = 0;

    virtual void setId(int id) = 0;
    /*friend bool operator ==(Node &a, Node &b){
	  if (a.getId() == b.getId()) return true;
	  else return false;
	}
	friend bool operator !=(Node &a, Node &b){
	  if (a.getId() == b.getId()) return false;
	  else return true;
	}*/
};

#endif
