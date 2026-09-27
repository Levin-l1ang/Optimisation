import re

from common.model.net import Net, NetSection


class NetReader:

    @staticmethod
    def parse_file(file_name: str) -> Net:
        net = Net()
        with open(file_name, "r", encoding="latin-1", newline="") as file:
            current_block = []  # type: [str]
            for line in file:
                line = line.rstrip()
                # print("Line " + line)
                if line == "$VISION":
                    # print("First line, skip")
                    continue
                elif (line == "" or line[0] == "*") and current_block:
                    # print("Empty line, finished block. Parse")
                    NetReader.parse_block(net, current_block)
                    current_block = []
                elif line[0] == "*":
                    # print("Comment, skip")
                    continue
                else:
                    # print("Append to block")
                    current_block.append(line)
        if current_block:
            NetReader.parse_block(net, current_block)
        return net

    @staticmethod
    def parse_block(net: Net, block: [str]) -> NetSection:
        if not block:
            raise ValueError("Cannot parse empty block")
        name, header = NetReader.parse_header(block[0])
        section = NetSection(name, header)
        for line in block[1:]:
            NetReader.parse_content(section, line)
        net.add_section(section)

    @staticmethod
    def parse_header(header_line: str) -> (str, [str]):
        splitted_header_line = header_line.split(":")
        if len(splitted_header_line) < 2:
            raise ValueError("Wrongly formatted header line, " + header_line)
        name = splitted_header_line[0].upper()
        headers = ":".join(splitted_header_line[1:])
        splitted_headers = [header.upper() for header in re.split("[;\t]", headers)]
        return name, splitted_headers

    @staticmethod
    def parse_content(section: NetSection, content_line: str) -> None:
        columns = re.split("[;\t]", content_line)
        section.append_column(columns)
