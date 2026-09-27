/**
 * Contains all in- and output classes.
 *
 * Every class follows the builder design pattern, i.e., there is an internal Builder class that is used for
 * configuration of the reader/writer.
 *
 * E.g. for a {@link net.lintim.io.LineReader}, there is {@link net.lintim.io.LineReader.Builder} object that gets
 * created by {@link net.lintim.io.LineReader.Builder#Builder(net.lintim.model.Graph)}, can be configured by using the
 * setter methods on the builder (i.e. {@link net.lintim.io.LineReader.Builder#setLineFileName(java.lang.String)}) and
 * in the end can be used to create a {@link net.lintim.io.LineReader} object by calling
 * {@link net.lintim.io.LineReader.Builder#build()}. For details on the different parameters of the reader and their
 * default values, have a look at the corresponding constructor documentation (e.g. here
 * {@link net.lintim.io.LineReader.Builder#Builder(net.lintim.model.Graph)}). Afterwards, call
 * {@link net.lintim.io.LineReader#read()} on the reader object to start the reading process and obtain the
 * {@link net.lintim.model.LinePool}.
 */
package net.lintim.io;