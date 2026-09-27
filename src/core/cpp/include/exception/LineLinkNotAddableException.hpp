#ifndef LINELINKNOTADDABLEEXCEPTION_HPP
#define LINELINKNOTADDABLEEXCEPTION_HPP

/**
 * Exception to throw if a link cannot be added to a line.
 */
class LineLinkNotAddableException : public std::runtime_error{
public:
    /**
     * Exception to throw if a link cannot be added to a line.
     *
     * @param linkId link id
     * @param lineId line id
     */
    LineLinkNotAddableException(int linkId, int lineId) : std::runtime_error(
        "Error L1: Link " + std::to_string(linkId) + " cannot be added to line " + std::to_string(lineId) + "."){}
};

#endif
